"""별도 임베딩 작업자(계획서 12절).

CSV 적재 트랜잭션 안에서 실행하지 않고, 별도 Python 실행 진입점으로
embedding IS NULL AND chunk_content <> '' 인 청크를 주기적으로 조회해
text-embedding-3-small 로 1536차원 벡터를 생성·저장한다.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

import psycopg
from psycopg import Connection
from tenacity import (
    Retrying,
    before_sleep_log,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

from .config import get_settings

logger = logging.getLogger(__name__)


# 작업자 한 번의 누적 처리 결과다.
@dataclass
class EmbeddingRunStats:
    """임베딩 작업 전체의 누적 처리 통계."""

    processed: int = 0
    success: int = 0
    failed: int = 0
    skipped: int = 0
    prompt_tokens: int = 0
    remaining: int = 0


# 비어 있지 않고 아직 벡터가 없는 청크만 잠정 처리 대상으로 가져온다.
_PENDING_SQL = (
    "SELECT id, chunk_content, content_hash FROM job_posting_chunks "
    "WHERE (embedding IS NULL OR embedding_model IS DISTINCT FROM %(model)s) "
    "AND chunk_content <> '' "
    "AND (NOT %(only_matching)s OR source_type NOT IN "
    "('PROCESS', 'DISQUALIFICATION', 'BENEFIT')) "
    "ORDER BY id LIMIT %(batch)s"
)

_COUNT_PENDING_SQL = (
    "SELECT COUNT(*) FROM job_posting_chunks "
    "WHERE (embedding IS NULL OR embedding_model IS DISTINCT FROM %(model)s) "
    "AND chunk_content <> '' "
    "AND (NOT %(only_matching)s OR source_type NOT IN "
    "('PROCESS', 'DISQUALIFICATION', 'BENEFIT'))"
)

_UPDATE_EMBEDDING_SQL = (
    "UPDATE job_posting_chunks "
    "SET embedding = %s::vector, embedding_model = %s, "
    "embedding_status = 'COMPLETED', embedding_updated_at = now() "
    "WHERE id = %s AND content_hash = %s "
    "AND (embedding IS NULL OR embedding_model IS DISTINCT FROM %s)"
)


class TransientEmbeddingError(Exception):
    """일시적 API 오류로 재시도 대상으로 분류."""


class InvalidEmbeddingResponseError(RuntimeError):
    """임베딩 API 응답 개수·인덱스·차원이 요청과 맞지 않을 때 사용한다."""


@dataclass(frozen=True)
class EmbeddingAPIResult:
    """OpenAI 응답에서 DB 저장에 필요한 값만 추린 결과."""

    vectors: list[list[float]]
    prompt_tokens: int


def _create_openai_client() -> Any:
    """설정된 API 키와 timeout으로 재사용 가능한 OpenAI 클라이언트를 만든다."""
    import openai

    settings = get_settings()
    return openai.OpenAI(
        api_key=settings.openai_api_key,
        timeout=settings.embedding_request_timeout_seconds,
        # 재시도는 아래 Retrying에서 통합 관리하므로 SDK 내부 재시도는 끈다.
        max_retries=0,
    )


def _request_embeddings(client: Any, texts: list[str]) -> Any:
    """OpenAI Embeddings API를 한 번 호출하고 일시적 오류만 재시도 예외로 변환한다."""
    import openai

    settings = get_settings()
    # "openai/text-embedding-3-small" -> "text-embedding-3-small"
    model = settings.embedding_model.split("/", 1)[-1]

    try:
        return client.embeddings.create(
            input=texts,
            model=model,
            dimensions=settings.embedding_dimension,
            encoding_format="float",
        )
    except (
        openai.RateLimitError,
        openai.APITimeoutError,
        openai.APIConnectionError,
        openai.InternalServerError,
    ) as exc:
        # 호출 제한·timeout·연결·5xx 오류는 지수 백오프로 재시도한다.
        raise TransientEmbeddingError(str(exc)) from exc


def _create_embeddings(
    texts: list[str],
    client: Any | None = None,
) -> EmbeddingAPIResult:
    """문자열 배치를 임베딩하고 요청 순서대로 벡터를 반환한다.

    설정의 EMBEDDING_MAX_RETRIES를 실제 재시도 횟수로 사용한다. 인증 실패나
    잘못된 요청처럼 재시도로 해결되지 않는 오류는 즉시 호출자에게 전달한다.
    """
    settings = get_settings()
    api_client = client or _create_openai_client()

    retrying = Retrying(
        retry=retry_if_exception_type(TransientEmbeddingError),
        stop=stop_after_attempt(settings.embedding_max_retries),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        reraise=True,
    )
    response = retrying(_request_embeddings, api_client, texts)

    # API는 각 결과에 원래 입력 위치인 index를 제공하므로 이를 기준으로 정렬한다.
    ordered = sorted(response.data, key=lambda item: item.index)
    expected_indexes = list(range(len(texts)))
    actual_indexes = [item.index for item in ordered]
    if actual_indexes != expected_indexes:
        raise InvalidEmbeddingResponseError(
            f"임베딩 응답 인덱스 불일치: expected={expected_indexes} "
            f"actual={actual_indexes}"
        )

    vectors = [item.embedding for item in ordered]
    for index, vector in enumerate(vectors):
        if len(vector) != settings.embedding_dimension:
            raise InvalidEmbeddingResponseError(
                f"임베딩 차원 불일치: index={index} "
                f"expected={settings.embedding_dimension} got={len(vector)}"
            )

    usage = getattr(response, "usage", None)
    prompt_tokens = int(getattr(usage, "prompt_tokens", 0) or 0)
    return EmbeddingAPIResult(vectors=vectors, prompt_tokens=prompt_tokens)


def _embedding_model_name() -> str:
    return get_settings().embedding_model.split("/", 1)[-1]


# ---------------------------------------------------------------------------
# 단일 임베딩 배치 처리
# ---------------------------------------------------------------------------
def _process_batch(
    conn: Connection,
    rows: list[tuple[int, str, str]],
    stats: EmbeddingRunStats,
    client: Any,
) -> bool:
    """한 배치를 API로 임베딩하고 해시가 그대로인 행만 원자적으로 저장한다.

    반환값이 false면 재시도 한도를 초과한 배치이므로 작업자는 같은 행을
    무한 반복하지 않고 현재 실행을 종료한다.
    """
    ids = [r[0] for r in rows]
    contents = [r[1] for r in rows]
    hashes_at_query = [r[2] for r in rows]

    # API 요청은 배치 단위지만 실패 통계는 다음 배치와 분리한다.
    try:
        api_result = _create_embeddings(contents, client=client)
    except Exception as exc:  # noqa: BLE001 - 일부 실패 격리
        stats.failed += len(rows)
        logger.error("배치 임베딩 실패 ids=%s: %s", ids, exc)
        return False
    stats.prompt_tokens += api_result.prompt_tokens

    # content_hash와 embedding IS NULL 조건을 UPDATE에 함께 넣어 검증과 저장을
    # 하나의 SQL로 처리한다. 처리 중 원문이 바뀌면 rowcount=0이 되어 폐기된다.
    with conn.cursor() as cur:
        for row_id, vector, hash_at_query in zip(
            ids,
            api_result.vectors,
            hashes_at_query,
        ):
            # json.dumps는 float 정밀도를 보존하면서 pgvector 입력 형식 [x,y,...]을 만든다.
            vector_text = json.dumps(vector, separators=(",", ":"))
            cur.execute(
                _UPDATE_EMBEDDING_SQL,
                (vector_text, _embedding_model_name(), row_id, hash_at_query,
                 _embedding_model_name()),
            )
            if cur.rowcount == 1:
                stats.success += 1
            else:
                stats.skipped += 1
                logger.info(
                    "임베딩 저장 스킵 id=%s: 삭제·해시 변경·선처리 중 하나",
                    row_id,
                )
    return True


def _count_pending(conn: Connection, only_matching: bool) -> int:
    """현재 임베딩 대기 청크 수를 반환한다."""
    with conn.cursor() as cur:
        cur.execute(_COUNT_PENDING_SQL, {
            "only_matching": only_matching, "model": _embedding_model_name()
        })
        return int(cur.fetchone()[0])


# ---------------------------------------------------------------------------
# 대기 청크가 없어질 때까지 배치 반복
# ---------------------------------------------------------------------------
def run_embedding_worker(
    conn: Connection,
    max_iterations: int = 0,
    batch_size: int | None = None,
    client: Any | None = None,
) -> EmbeddingRunStats:
    """미임베딩·비어있지 않은 매칭 청크를 배치 단위로 임베딩.

    max_iterations=0 이면 pending 이 없어질 때까지 반복한다.
    """
    settings = get_settings()
    stats = EmbeddingRunStats()
    iteration = 0
    effective_batch_size = batch_size or settings.embedding_batch_size
    api_client = client or _create_openai_client()

    # 공식 API의 문자열 입력 배열 최대 개수(2,048)를 넘지 않도록 방어한다.
    if not 1 <= effective_batch_size <= 2048:
        raise ValueError("batch_size는 1~2048 범위여야 합니다.")

    initial_pending = _count_pending(conn, settings.embedding_only_matching)
    logger.info(
        "임베딩 대상 조회 완료: pending=%d batch_size=%d model=%s dimension=%d",
        initial_pending,
        effective_batch_size,
        _embedding_model_name(),
        settings.embedding_dimension,
    )

    # max_iterations=0이면 대기 행이 없어질 때까지 계속 처리한다.
    while True:
        iteration += 1
        if max_iterations and iteration > max_iterations:
            break
        with conn.cursor() as cur:
            cur.execute(
                _PENDING_SQL,
                {"only_matching": settings.embedding_only_matching,
                 "batch": effective_batch_size,
                 "model": _embedding_model_name()},
            )
            rows = cur.fetchall()
        if not rows:
            logger.info("대기 중인 임베딩 청크가 없습니다.")
            break
        logger.info(
            "임베딩 배치 시작: iteration=%d batch_rows=%d first_id=%s last_id=%s",
            iteration,
            len(rows),
            rows[0][0],
            rows[-1][0],
        )
        stats.processed += len(rows)
        success_before = stats.success
        failed_before = stats.failed
        skipped_before = stats.skipped
        batch_succeeded = _process_batch(conn, rows, stats, api_client)
        if not batch_succeeded:
            # 실패 배치가 계속 pending으로 남아 같은 데이터를 무한 호출하지 않게 중단한다.
            conn.rollback()
            logger.error(
                "재시도 한도 초과로 작업 중단: iteration=%d failed_rows=%d",
                iteration,
                len(rows),
            )
            break
        conn.commit()
        logger.info(
            "임베딩 배치 완료: iteration=%d success=%d failed=%d skipped=%d "
            "cumulative_processed=%d",
            iteration,
            stats.success - success_before,
            stats.failed - failed_before,
            stats.skipped - skipped_before,
            stats.processed,
        )

    stats.remaining = _count_pending(conn, settings.embedding_only_matching)
    logger.info(
        "임베딩 작업 종료: processed=%d success=%d failed=%d skipped=%d "
        "prompt_tokens=%d remaining=%d model=%s",
        stats.processed, stats.success, stats.failed, stats.skipped,
        stats.prompt_tokens, stats.remaining,
        _embedding_model_name(),
    )
    return stats


# psycopg connection 없이 모델명만 확인하는 helper(테스트용)
def _ensure_psycopg_version() -> None:
    _ = psycopg  # noqa: F841

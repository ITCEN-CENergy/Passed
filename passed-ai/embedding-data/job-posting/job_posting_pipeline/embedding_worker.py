"""별도 임베딩 작업자(계획서 12절).

CSV 적재 트랜잭션 안에서 실행하지 않고, 별도 Python 실행 진입점으로
embedding IS NULL AND chunk_content <> '' 인 청크를 주기적으로 조회해
text-embedding-3-small 로 1536차원 벡터를 생성·저장한다.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import psycopg
from psycopg import Connection
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

from .config import get_settings

logger = logging.getLogger(__name__)


@dataclass
class EmbeddingRunStats:
    processed: int = 0
    success: int = 0
    failed: int = 0
    skipped: int = 0


_PENDING_SQL = (
    "SELECT id, chunk_content, content_hash FROM job_posting_chunks "
    "WHERE embedding IS NULL AND chunk_content <> '' "
    "AND (NOT %(only_matching)s OR use_for_matching = true) "
    "ORDER BY id LIMIT %(batch)s"
)

_HASH_CHECK_SQL = "SELECT content_hash FROM job_posting_chunks WHERE id = %s"

_UPDATE_EMBEDDING_SQL = (
    "UPDATE job_posting_chunks SET embedding = %s::vector WHERE id = %s"
)


class TransientEmbeddingError(Exception):
    """일시적 API 오류로 재시도 대상으로 분류."""


@retry(
    retry=retry_if_exception_type(TransientEmbeddingError),
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=30),
    reraise=True,
)
def _create_embeddings(texts: list[str]) -> list[list[float]]:
    """OpenAI 임베딩 API 호출(일시적 오류는 지수 백오프 재시도)."""
    import openai

    settings = get_settings()
    # "openai/text-embedding-3-small" -> "text-embedding-3-small"
    model = settings.embedding_model.split("/", 1)[-1]
    client = openai.OpenAI(api_key=settings.openai_api_key)
    try:
        resp = client.embeddings.create(
            input=texts, model=model, dimensions=settings.embedding_dimension
        )
    except openai.APIError as exc:
        raise TransientEmbeddingError(str(exc)) from exc
    return [d.embedding for d in resp.data]


def _embedding_model_name() -> str:
    return get_settings().embedding_model.split("/", 1)[-1]


def _process_batch(
    conn: Connection, rows: list[tuple[int, str, str]], stats: EmbeddingRunStats
) -> None:
    ids = [r[0] for r in rows]
    contents = [r[1] for r in rows]
    hashes_at_query = [r[2] for r in rows]

    try:
        vectors = _create_embeddings(contents)
    except Exception as exc:  # noqa: BLE001 - 일부 실패 격리
        stats.failed += len(rows)
        logger.error("배치 임베딩 실패 ids=%s: %s", ids, exc)
        return

    # 차원 검증
    settings = get_settings()
    for vec in vectors:
        if len(vec) != settings.embedding_dimension:
            stats.failed += len(rows)
            logger.error(
                "임베딩 차원 불일치: expected=%d got=%d",
                settings.embedding_dimension, len(vec),
            )
            return

    # 저장 직전 content_hash 재검증
    with conn.cursor() as cur:
        for row_id, vec, h_at_query in zip(ids, vectors, hashes_at_query):
            cur.execute(_HASH_CHECK_SQL, (row_id,))
            current = cur.fetchone()
            if current is None:
                stats.skipped += 1
                continue
            if current[0] != h_at_query:
                # 원문이 바뀌었으면 폐기
                stats.skipped += 1
                logger.info(
                    "임베딩 폐기 id=%s: 저장 전 해시 변경(%s -> %s)",
                    row_id, h_at_query[:12], current[0][:12],
                )
                continue
            vec_text = "[" + ",".join(f"{x:.8f}" for x in vec) + "]"
            cur.execute(_UPDATE_EMBEDDING_SQL, (vec_text, row_id))
            stats.success += 1


def run_embedding_worker(conn: Connection, max_iterations: int = 0) -> EmbeddingRunStats:
    """미임베딩·비어있지 않은 매칭 청크를 배치 단위로 임베딩.

    max_iterations=0 이면 pending 이 없어질 때까지 반복한다.
    """
    settings = get_settings()
    stats = EmbeddingRunStats()
    iteration = 0

    while True:
        iteration += 1
        if max_iterations and iteration > max_iterations:
            break
        with conn.cursor() as cur:
            cur.execute(
                _PENDING_SQL,
                {"only_matching": settings.embedding_only_matching,
                 "batch": settings.embedding_batch_size},
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
        _process_batch(conn, rows, stats)
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

    logger.info(
        "임베딩 작업 종료: processed=%d success=%d failed=%d skipped=%d model=%s",
        stats.processed, stats.success, stats.failed, stats.skipped,
        _embedding_model_name(),
    )
    return stats


# psycopg connection 없이 모델명만 확인하는 helper(테스트용)
def _ensure_psycopg_version() -> None:
    _ = psycopg  # noqa: F841

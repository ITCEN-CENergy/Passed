"""이력서·자기소개서 청크를 같은 규칙으로 임베딩하는 배치 작업자."""

from __future__ import annotations

import logging
import os
from typing import Any

from tenacity import (
    Retrying,
    before_sleep_log,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

from .models import EmbeddingStats

logger = logging.getLogger(__name__)

EMBEDDING_MODEL = os.getenv(
    "EMBEDDING_MODEL", "text-embedding-3-small"
).split("/", 1)[-1]
EMBEDDING_DIM = int(os.getenv("EMBEDDING_DIMENSION", "1536"))
EMBEDDING_BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "100"))
EMBEDDING_MAX_RETRIES = 3
EMBEDDING_REQUEST_TIMEOUT_SECONDS = float(
    os.getenv("EMBEDDING_REQUEST_TIMEOUT_SECONDS", "60")
)

RESUME_USER_FILTER_SQL = (
    "AND resume_id IN (SELECT id FROM resumes WHERE user_id = %s)"
)
COVER_LETTER_USER_FILTER_SQL = (
    "AND cover_letter_item_id IN ("
    "SELECT ci.id FROM cover_letter_items ci "
    "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
    "WHERE cl.user_id = %s)"
)

_ALLOWED_FILTERS = {
    "resume_chunks": {"", RESUME_USER_FILTER_SQL},
    "cover_letter_chunks": {"", COVER_LETTER_USER_FILTER_SQL},
}


class TransientEmbeddingError(RuntimeError):
    """재시도하면 성공할 가능성이 있는 OpenAI API 오류."""


class InvalidEmbeddingResponseError(RuntimeError):
    """API 응답의 개수·순서·벡터 차원이 요청 계약과 다를 때 발생한다."""


def _validate_worker_args(table: str, filter_sql: str, batch_size: int) -> None:
    if table not in _ALLOWED_FILTERS:
        raise ValueError(f"지원하지 않는 임베딩 테이블입니다: {table}")
    if filter_sql not in _ALLOWED_FILTERS[table]:
        raise ValueError(f"{table}에 허용되지 않은 필터 SQL입니다.")
    if not 1 <= batch_size <= 2048:
        raise ValueError("batch_size는 1~2048 범위여야 합니다.")


def _create_openai_client() -> Any:
    import openai

    return openai.OpenAI(
        api_key=os.getenv("OPENAI_API_KEY"),
        timeout=EMBEDDING_REQUEST_TIMEOUT_SECONDS,
        max_retries=0,
    )


def _request_embeddings(client: Any, texts: list[str]) -> Any:
    import openai

    try:
        return client.embeddings.create(
            input=texts,
            model=EMBEDDING_MODEL,
            dimensions=EMBEDDING_DIM,
            encoding_format="float",
        )
    except (
        openai.RateLimitError,
        openai.APITimeoutError,
        openai.APIConnectionError,
        openai.InternalServerError,
    ) as exc:
        raise TransientEmbeddingError(str(exc)) from exc


def _create_embeddings(client: Any, texts: list[str]) -> list[list[float]]:
    retrying = Retrying(
        retry=retry_if_exception_type(TransientEmbeddingError),
        stop=stop_after_attempt(EMBEDDING_MAX_RETRIES),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        reraise=True,
    )
    response = retrying(_request_embeddings, client, texts)
    ordered = sorted(response.data, key=lambda item: item.index)
    actual_indexes = [int(item.index) for item in ordered]
    expected_indexes = list(range(len(texts)))
    if actual_indexes != expected_indexes:
        raise InvalidEmbeddingResponseError(
            f"임베딩 응답 index 불일치: expected={expected_indexes} "
            f"actual={actual_indexes}"
        )

    vectors = [list(item.embedding) for item in ordered]
    for index, vector in enumerate(vectors):
        if len(vector) != EMBEDDING_DIM:
            raise InvalidEmbeddingResponseError(
                f"임베딩 차원 불일치: index={index} "
                f"expected={EMBEDDING_DIM} actual={len(vector)}"
            )
    return vectors


def _row_values(row: Any) -> tuple[int, str, str]:
    if isinstance(row, dict):
        return int(row["id"]), str(row["chunk_content"]), str(row["content_hash"])
    return int(row[0]), str(row[1]), str(row[2])


def _count_by_content_condition(
    conn: Any,
    table: str,
    filter_sql: str,
    filter_params: tuple[Any, ...],
    content_condition: str,
) -> int:
    with conn.cursor() as cur:
        cur.execute(
            f"SELECT COUNT(*) FROM {table} "
            "WHERE embedding_status = 'PENDING' "
            f"{filter_sql} AND {content_condition}",
            filter_params,
        )
        row = cur.fetchone()
    if isinstance(row, dict):
        return int(next(iter(row.values())))
    return int(row[0])


def count_pending_chunks(
    conn: Any,
    table: str,
    *,
    filter_sql: str = "",
    filter_params: tuple[Any, ...] = (),
) -> int:
    """dry-run에서 사용할, API 호출 가능한 PENDING 청크 개수."""
    _validate_worker_args(table, filter_sql, EMBEDDING_BATCH_SIZE)
    return _count_by_content_condition(
        conn,
        table,
        filter_sql,
        filter_params,
        "BTRIM(chunk_content) <> ''",
    )


def _fetch_pending_batch(
    conn: Any,
    table: str,
    filter_sql: str,
    filter_params: tuple[Any, ...],
    batch_size: int,
) -> list[tuple[int, str, str]]:
    with conn.cursor() as cur:
        cur.execute(
            f"SELECT id, chunk_content, content_hash FROM {table} "
            "WHERE embedding_status = 'PENDING' "
            f"{filter_sql} AND BTRIM(chunk_content) <> '' "
            "ORDER BY id LIMIT %s",
            (*filter_params, batch_size),
        )
        return [_row_values(row) for row in cur.fetchall()]


def _save_completed_batch(
    conn: Any,
    table: str,
    rows: list[tuple[int, str, str]],
    vectors: list[list[float]],
) -> tuple[int, int]:
    embedded = skipped = 0
    with conn.cursor() as cur:
        for (row_id, _content, content_hash), vector in zip(rows, vectors):
            cur.execute(
                f"UPDATE {table} SET embedding = %s, embedding_model = %s, "
                "embedding_status = 'COMPLETED', embedding_updated_at = now() "
                "WHERE id = %s AND content_hash = %s "
                "AND embedding_status = 'PENDING'",
                (vector, EMBEDDING_MODEL, row_id, content_hash),
            )
            if cur.rowcount == 1:
                embedded += 1
            else:
                skipped += 1
                logger.info(
                    "임베딩 저장 생략 table=%s id=%s: 원문 변경·삭제·선처리",
                    table,
                    row_id,
                )
    return embedded, skipped


def _mark_failed_batch(
    conn: Any,
    table: str,
    rows: list[tuple[int, str, str]],
) -> tuple[int, int]:
    failed = skipped = 0
    with conn.cursor() as cur:
        for row_id, _content, content_hash in rows:
            cur.execute(
                f"UPDATE {table} SET embedding_status = 'FAILED' "
                "WHERE id = %s AND content_hash = %s "
                "AND embedding_status = 'PENDING'",
                (row_id, content_hash),
            )
            if cur.rowcount == 1:
                failed += 1
            else:
                skipped += 1
    return failed, skipped


def embed_pending_chunks(
    conn: Any,
    table: str,
    *,
    filter_sql: str = "",
    filter_params: tuple[Any, ...] = (),
    batch_size: int = EMBEDDING_BATCH_SIZE,
) -> EmbeddingStats:
    """한 테이블의 PENDING 청크를 배치별로 임베딩하고 즉시 커밋한다."""
    _validate_worker_args(table, filter_sql, batch_size)

    invalid_empty = _count_by_content_condition(
        conn,
        table,
        filter_sql,
        filter_params,
        "BTRIM(chunk_content) = ''",
    )
    if invalid_empty:
        logger.warning(
            "공백 청크 발견 table=%s count=%s: 상태는 변경하지 않습니다.",
            table,
            invalid_empty,
        )

    embedded = failed = 0
    skipped = invalid_empty
    client: Any | None = None
    iteration = 0

    while True:
        rows = _fetch_pending_batch(
            conn,
            table,
            filter_sql,
            filter_params,
            batch_size,
        )
        if not rows:
            break

        iteration += 1
        if client is None:
            client = _create_openai_client()
        logger.info(
            "임베딩 배치 시작 table=%s iteration=%s rows=%s first_id=%s last_id=%s",
            table,
            iteration,
            len(rows),
            rows[0][0],
            rows[-1][0],
        )

        try:
            vectors = _create_embeddings(client, [row[1] for row in rows])
        except Exception as exc:
            batch_failed, batch_skipped = _mark_failed_batch(conn, table, rows)
            failed += batch_failed
            skipped += batch_skipped
            conn.commit()
            logger.error(
                "임베딩 배치 실패 table=%s ids=%s error=%s",
                table,
                [row[0] for row in rows],
                exc,
            )
            continue

        batch_embedded, batch_skipped = _save_completed_batch(
            conn,
            table,
            rows,
            vectors,
        )
        embedded += batch_embedded
        skipped += batch_skipped

        conn.commit()
        logger.info(
            "임베딩 배치 완료 table=%s embedded=%s skipped=%s",
            table,
            batch_embedded,
            batch_skipped,
        )

    return EmbeddingStats(embedded=embedded, failed=failed, skipped=skipped)

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any, Sequence

from psycopg import Connection
from tenacity import Retrying, before_sleep_log, retry_if_exception_type
from tenacity import stop_after_attempt, wait_exponential

from .config import get_settings

logger = logging.getLogger(__name__)


class TransientEmbeddingError(Exception):
    """호출 제한, 연결 오류, timeout, 서버 오류처럼 재시도 가능한 오류."""


class InvalidEmbeddingResponseError(RuntimeError):
    """응답 개수, 인덱스 또는 벡터 차원이 올바르지 않은 경우."""


class SkillDataValidationError(ValueError):
    """스킬의 name 또는 description이 임베딩에 부적합한 경우."""


@dataclass(frozen=True)
class SkillRow:
    skill_id: int
    name: str
    description: str


@dataclass(frozen=True)
class EmbeddingAPIResult:
    vectors: list[list[float]]
    prompt_tokens: int


@dataclass
class RunStats:
    selected: int = 0
    success: int = 0
    failed: int = 0
    skipped: int = 0
    prompt_tokens: int = 0
    missing: int = 0
    wrong_dimension: int = 0


def build_embedding_input(name: str, description: str) -> str:
    name = name.strip() if isinstance(name, str) else ""
    description = description.strip() if isinstance(description, str) else ""
    if not name or not description:
        raise SkillDataValidationError("name과 description은 비어 있을 수 없습니다.")
    return f"스킬명: {name}\n설명: {description}"


def _client() -> Any:
    import openai

    settings = get_settings()
    return openai.OpenAI(
        api_key=settings.openai_api_key,
        timeout=settings.embedding_timeout_seconds,
        max_retries=0,
    )


def _request(client: Any, inputs: list[str]) -> Any:
    import openai

    settings = get_settings()
    try:
        return client.embeddings.create(
            model=settings.embedding_model.split("/", 1)[-1],
            input=inputs,
            dimensions=settings.embedding_dimension,
            encoding_format="float",
        )
    except (
        openai.RateLimitError,
        openai.APITimeoutError,
        openai.APIConnectionError,
        openai.InternalServerError,
    ) as exc:
        raise TransientEmbeddingError(str(exc)) from exc


def create_embeddings(inputs: list[str], client: Any | None = None) -> EmbeddingAPIResult:
    settings = get_settings()
    api_client = client or _client()
    response = Retrying(
        retry=retry_if_exception_type(TransientEmbeddingError),
        stop=stop_after_attempt(settings.embedding_max_retries),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        reraise=True,
    )(_request, api_client, inputs)

    ordered = sorted(response.data, key=lambda item: item.index)
    indexes = [item.index for item in ordered]
    if indexes != list(range(len(inputs))):
        raise InvalidEmbeddingResponseError(
            f"응답 인덱스 불일치: expected={list(range(len(inputs)))} actual={indexes}"
        )
    vectors = [item.embedding for item in ordered]
    for index, vector in enumerate(vectors):
        if len(vector) != settings.embedding_dimension:
            raise InvalidEmbeddingResponseError(
                f"임베딩 차원 불일치: index={index} "
                f"expected={settings.embedding_dimension} actual={len(vector)}"
            )
    usage = getattr(response, "usage", None)
    return EmbeddingAPIResult(
        vectors=vectors,
        prompt_tokens=int(getattr(usage, "prompt_tokens", 0) or 0),
    )


def _snapshot(conn: Connection) -> list[tuple[Any, ...]]:
    """embedding 외 기존 컬럼의 불변성 검증용 스냅샷."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, category, name, description, created_at "
            "FROM skills ORDER BY id"
        )
        return cur.fetchall()


def _load_rows(conn: Connection, force: bool) -> list[SkillRow]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, description, embedding IS NULL FROM skills ORDER BY id"
        )
        raw_rows = cur.fetchall()
    settings = get_settings()
    if len(raw_rows) != settings.expected_skill_count:
        raise SkillDataValidationError(
            f"스킬 건수 불일치: expected={settings.expected_skill_count} actual={len(raw_rows)}"
        )
    all_rows = [SkillRow(int(row[0]), row[1], row[2]) for row in raw_rows]
    invalid = [
        row.skill_id for row in all_rows
        if not isinstance(row.name, str) or not row.name.strip()
        or not isinstance(row.description, str) or not row.description.strip()
    ]
    if invalid:
        raise SkillDataValidationError(
            f"name/description 결측 skill_id={invalid[:20]} (총 {len(invalid)}건)"
        )
    return [
        row for row, raw in zip(all_rows, raw_rows)
        if force or bool(raw[3])
    ]


def _update_batch(
    conn: Connection,
    rows: Sequence[SkillRow],
    vectors: Sequence[list[float]],
) -> tuple[int, int]:
    success = skipped = 0
    with conn.cursor() as cur:
        for row, vector in zip(rows, vectors):
            cur.execute(
                "UPDATE skills SET embedding = %s::vector "
                "WHERE id = %s AND name = %s AND description = %s",
                (json.dumps(vector, separators=(",", ":")), row.skill_id,
                 row.name, row.description),
            )
            if cur.rowcount == 1:
                success += 1
            else:
                skipped += 1
    return success, skipped


def validate_embeddings(conn: Connection) -> tuple[int, int]:
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM skills WHERE embedding IS NULL")
        missing = int(cur.fetchone()[0])
        cur.execute(
            "SELECT COUNT(*) FROM skills WHERE embedding IS NOT NULL "
            "AND vector_dims(embedding) <> %s",
            (get_settings().embedding_dimension,),
        )
        wrong_dimension = int(cur.fetchone()[0])
    return missing, wrong_dimension


def run(conn: Connection, *, force: bool = False, batch_size: int | None = None,
        client: Any | None = None) -> RunStats:
    settings = get_settings()
    size = batch_size or settings.embedding_batch_size
    if not 1 <= size <= 2048:
        raise ValueError("batch_size는 1~2048 범위여야 합니다.")

    before = _snapshot(conn)
    rows = _load_rows(conn, force)
    stats = RunStats(selected=len(rows))
    api_client = client or _client()

    for start in range(0, len(rows), size):
        batch = rows[start:start + size]
        inputs = [build_embedding_input(row.name, row.description) for row in batch]
        try:
            result = create_embeddings(inputs, api_client)
            success, skipped = _update_batch(conn, batch, result.vectors)
            conn.commit()
            stats.success += success
            stats.skipped += skipped
            stats.prompt_tokens += result.prompt_tokens
            logger.info("배치 완료 %d/%d", min(start + size, len(rows)), len(rows))
        except Exception:
            conn.rollback()
            stats.failed += len(batch)
            logger.exception("배치 실패 skill_id=%s", [row.skill_id for row in batch])
            break

    after = _snapshot(conn)
    if before != after:
        raise RuntimeError("embedding 외 기존 스킬 데이터가 변경되었습니다.")
    stats.missing, stats.wrong_dimension = validate_embeddings(conn)
    return stats


def similar_skills(conn: Connection, query: str, *, category: str | None = None,
                   limit: int = 10, client: Any | None = None) -> list[tuple[Any, ...]]:
    vector = create_embeddings([query.strip()], client).vectors[0]
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, category, 1 - (embedding <=> %s::vector) AS similarity "
            "FROM skills WHERE embedding IS NOT NULL "
            "AND (%s::varchar IS NULL OR category = %s) "
            "ORDER BY embedding <=> %s::vector LIMIT %s",
            (json.dumps(vector), category, category, json.dumps(vector), limit),
        )
        return cur.fetchall()

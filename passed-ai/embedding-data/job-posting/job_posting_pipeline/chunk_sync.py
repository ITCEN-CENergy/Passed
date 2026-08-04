"""청크 변경 감지와 동기화(계획서 11절).

공고 하나의 청크 동기화는 하나의 DB 트랜잭션에서 처리한다.
동일 (job_posting_id, source_type, chunk_index) 키 기준으로:
- 같은 키·같은 해시: 변경 없음(기존 임베딩 유지)
- 같은 키·다른 해시: 내용/해시 갱신, embedding=NULL
- 새 키: INSERT
- 사라진 키: DELETE(벡터도 함께 삭제)
동일 공고·동일 source_type 범위에서 해시 기반 임베딩 재사용을 먼저 시도한다.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from psycopg import Connection

from .config import get_settings
from .models import Chunk

logger = logging.getLogger(__name__)


# 한 공고를 동기화한 결과를 실행 로그와 집계에 전달한다.
@dataclass
class SyncStats:
    inserted: int = 0
    updated: int = 0
    unchanged: int = 0
    deleted: int = 0
    embedded_reused: int = 0


_SELECT_EXISTING = (
    "SELECT id, source_type, chunk_index, content_hash, embedding::text, "
    "embedding_model "
    "FROM job_posting_chunks WHERE job_posting_id = %s"
)

# Flyway V3의 ck_job_posting_chunk_source_type 허용값.
DB_SOURCE_TYPES = frozenset({
    "POSITION_DETAIL", "MAIN_TASK", "REQUIREMENT", "PREFERENCE",
    "BENEFIT", "PROCESS", "DISQUALIFICATION",
})


@dataclass
class _ExistingRow:
    id: int
    source_type: str
    chunk_index: int
    content_hash: str
    embedding_text: str | None
    embedding_model: str | None


# ---------------------------------------------------------------------------
# 기존 청크 조회와 임베딩 재사용 후보 구성
# ---------------------------------------------------------------------------
def _load_existing(conn: Connection, job_posting_id: int) -> list[_ExistingRow]:
    with conn.cursor() as cur:
        cur.execute(_SELECT_EXISTING, (job_posting_id,))
        return [
            _ExistingRow(r[0], r[1], r[2], r[3], r[4], r[5])
            for r in cur.fetchall()
        ]


def _build_reuse_map(
    existing: list[_ExistingRow], embedding_model: str
) -> dict[str, dict[str, str]]:
    """source_type -> {content_hash: embedding_text} (embedding 이 있는 행만)."""
    reuse: dict[str, dict[str, str]] = {}
    # 같은 source_type·content_hash의 기존 벡터만 재사용한다.
    for row in existing:
        if row.embedding_text and row.embedding_model == embedding_model:
            reuse.setdefault(row.source_type, {})[row.content_hash] = row.embedding_text
    return reuse


_INSERT_SQL = (
    "INSERT INTO job_posting_chunks "
    "(job_posting_id, source_type, chunk_index, chunk_content, "
    "embedding, embedding_model, embedding_status, embedding_updated_at, content_hash) "
    "VALUES (%s, %s, %s, %s, %s::vector, %s, %s, "
    "CASE WHEN %s::boolean THEN now() ELSE NULL END, %s)"
)

_UPDATE_SQL = (
    "UPDATE job_posting_chunks SET chunk_content = %s, content_hash = %s, "
    "embedding = %s::vector, embedding_model = %s, embedding_status = %s, "
    "embedding_updated_at = CASE WHEN %s::boolean THEN now() ELSE NULL END "
    "WHERE id = %s"
)

_DELETE_SQL = "DELETE FROM job_posting_chunks WHERE id = %s"


# ---------------------------------------------------------------------------
# 신규 청크와 DB 상태 비교·반영
# ---------------------------------------------------------------------------
def sync_posting(conn: Connection, job_posting_id: int, new_chunks: list[Chunk]) -> SyncStats:
    """공고 하나의 청크를 동기화. 호출자가 트랜잭션 commit/rollback 을 담당한다."""
    stats = SyncStats()
    existing = _load_existing(conn, job_posting_id)
    embedding_model = get_settings().embedding_model.split("/", 1)[-1]
    reuse_map = _build_reuse_map(existing, embedding_model)
    persistable_chunks = [
        chunk for chunk in new_chunks
        if chunk.chunk_content.strip() and chunk.source_type.value in DB_SOURCE_TYPES
    ]
    skipped = len(new_chunks) - len(persistable_chunks)
    if skipped:
        logger.info(
            "DB 계약상 저장 제외 job_posting_id=%s count=%d",
            job_posting_id, skipped,
        )

    existing_by_key: dict[tuple[str, int], _ExistingRow] = {
        (r.source_type, r.chunk_index): r for r in existing
    }
    new_keys: set[tuple[str, int]] = {
        (c.source_type.value, c.chunk_index) for c in persistable_chunks
    }

    inserts: list[tuple] = []
    updates: list[tuple] = []
    deletes: list[int] = []

    # 키와 해시를 비교해 INSERT/UPDATE/유지 중 하나로 분류한다.
    for c in persistable_chunks:
        key = (c.source_type.value, c.chunk_index)
        reuse_text = reuse_map.get(c.source_type.value, {}).get(c.content_hash)
        if reuse_text:
            stats.embedded_reused += 1

        row = existing_by_key.get(key)
        if row is None:
            # 새 키: INSERT
            reused = reuse_text is not None
            inserts.append((
                job_posting_id, c.source_type.value, c.chunk_index,
                c.chunk_content, reuse_text,
                embedding_model if reused else None,
                "COMPLETED" if reused else "PENDING",
                reused, c.content_hash,
            ))
        elif row.content_hash == c.content_hash:
            # 같은 키·같은 해시: 유지
            stats.unchanged += 1
        else:
            # 같은 키·다른 해시: 갱신(임베딩 재사용 또는 NULL)
            reused = reuse_text is not None
            updates.append((
                c.chunk_content, c.content_hash, reuse_text,
                embedding_model if reused else None,
                "COMPLETED" if reused else "PENDING",
                reused, row.id,
            ))

    # 사라진 키: DELETE
    for key, row in existing_by_key.items():
        if key not in new_keys:
            deletes.append(row.id)

    # 분류가 끝난 뒤 DB 작업을 묶어서 실행해 왕복 횟수를 줄인다.
    with conn.cursor() as cur:
        if inserts:
            cur.executemany(_INSERT_SQL, inserts)
            stats.inserted += len(inserts)
        if updates:
            cur.executemany(_UPDATE_SQL, updates)
            stats.updated += len(updates)
        if deletes:
            cur.executemany(_DELETE_SQL, [(d,) for d in deletes])
            stats.deleted += len(deletes)

    logger.info(
        "청크 동기화 job_posting_id=%s inserted=%d updated=%d unchanged=%d deleted=%d reuse=%d",
        job_posting_id, stats.inserted, stats.updated, stats.unchanged,
        stats.deleted, stats.embedded_reused,
    )
    return stats

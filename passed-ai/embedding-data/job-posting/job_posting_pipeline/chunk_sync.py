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

from .models import Chunk

logger = logging.getLogger(__name__)


@dataclass
class SyncStats:
    inserted: int = 0
    updated: int = 0
    unchanged: int = 0
    deleted: int = 0
    embedded_reused: int = 0


_SELECT_EXISTING = (
    "SELECT id, source_type, chunk_index, content_hash, embedding::text "
    "FROM job_posting_chunks WHERE job_posting_id = %s"
)


@dataclass
class _ExistingRow:
    id: int
    source_type: str
    chunk_index: int
    content_hash: str
    embedding_text: str | None


def _load_existing(conn: Connection, job_posting_id: int) -> list[_ExistingRow]:
    with conn.cursor() as cur:
        cur.execute(_SELECT_EXISTING, (job_posting_id,))
        return [
            _ExistingRow(r[0], r[1], r[2], r[3], r[4]) for r in cur.fetchall()
        ]


def _build_reuse_map(existing: list[_ExistingRow]) -> dict[str, dict[str, str]]:
    """source_type -> {content_hash: embedding_text} (embedding 이 있는 행만)."""
    reuse: dict[str, dict[str, str]] = {}
    for row in existing:
        if row.embedding_text:
            reuse.setdefault(row.source_type, {})[row.content_hash] = row.embedding_text
    return reuse


_INSERT_SQL = (
    "INSERT INTO job_posting_chunks "
    "(job_posting_id, source_type, chunk_index, chunk_content, "
    "use_for_matching, embedding, content_hash) "
    "VALUES (%s, %s, %s, %s, %s, %s::vector, %s)"
)

_UPDATE_SQL = (
    "UPDATE job_posting_chunks SET chunk_content = %s, content_hash = %s, "
    "embedding = %s::vector WHERE id = %s"
)

_DELETE_SQL = "DELETE FROM job_posting_chunks WHERE id = %s"


def sync_posting(conn: Connection, job_posting_id: int, new_chunks: list[Chunk]) -> SyncStats:
    """공고 하나의 청크를 동기화. 호출자가 트랜잭션 commit/rollback 을 담당한다."""
    stats = SyncStats()
    existing = _load_existing(conn, job_posting_id)
    reuse_map = _build_reuse_map(existing)

    existing_by_key: dict[tuple[str, int], _ExistingRow] = {
        (r.source_type, r.chunk_index): r for r in existing
    }
    new_keys: set[tuple[str, int]] = {
        (c.source_type.value, c.chunk_index) for c in new_chunks
    }

    inserts: list[tuple] = []
    updates: list[tuple] = []
    deletes: list[int] = []

    for c in new_chunks:
        key = (c.source_type.value, c.chunk_index)
        reuse_text = reuse_map.get(c.source_type.value, {}).get(c.content_hash)
        if reuse_text:
            stats.embedded_reused += 1

        row = existing_by_key.get(key)
        if row is None:
            # 새 키: INSERT
            inserts.append((
                job_posting_id, c.source_type.value, c.chunk_index,
                c.chunk_content, c.use_for_matching_flag,
                reuse_text, c.content_hash,
            ))
        elif row.content_hash == c.content_hash:
            # 같은 키·같은 해시: 유지
            stats.unchanged += 1
        else:
            # 같은 키·다른 해시: 갱신(임베딩 재사용 또는 NULL)
            emb = reuse_text  # 같은 해시의 기존 임베딩이 있으면 재사용, 없으면 None
            updates.append((c.chunk_content, c.content_hash, emb, row.id))

    # 사라진 키: DELETE
    for key, row in existing_by_key.items():
        if key not in new_keys:
            deletes.append(row.id)

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

"""해시 비교로 변경된 청크만 갱신하는 DB 동기화."""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from .models import CoverLetterChunk, ResumeChunk, SyncStats


def _values(row: Any, columns: tuple[str, ...]) -> tuple[Any, ...]:
    if isinstance(row, dict):
        return tuple(row[column] for column in columns)
    return tuple(row)


def sync_resume_chunks(
    conn: Any,
    resume_id: int,
    chunks: Iterable[ResumeChunk],
) -> SyncStats:
    """한 이력서의 전체 청크 스냅샷을 동기화한다. commit은 호출자가 담당한다."""
    desired_list = list(chunks)
    desired = {chunk.key: chunk for chunk in desired_list}
    if len(desired) != len(desired_list):
        raise ValueError("resume chunk key가 중복되었습니다.")
    if any(chunk.resume_id != resume_id for chunk in desired_list):
        raise ValueError("다른 resume_id의 청크가 포함되어 있습니다.")

    inserted = updated = deleted = unchanged = 0
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM resumes WHERE id = %s FOR UPDATE", (resume_id,))
        if cur.fetchone() is None:
            raise ValueError(f"resume을 찾을 수 없습니다: {resume_id}")

        cur.execute(
            "SELECT source_type, source_id, chunk_index, content_hash "
            "FROM resume_chunks WHERE resume_id = %s",
            (resume_id,),
        )
        existing = {
            (str(source_type), int(source_id), int(chunk_index)): content_hash
            for source_type, source_id, chunk_index, content_hash in (
                _values(row, ("source_type", "source_id", "chunk_index", "content_hash"))
                for row in cur.fetchall()
            )
        }

        for key, chunk in desired.items():
            previous_hash = existing.get(key)
            if previous_hash == chunk.content_hash:
                unchanged += 1
                continue
            if key in existing:
                cur.execute(
                    "UPDATE resume_chunks SET chunk_content = %s, content_hash = %s, "
                    "embedding = NULL, embedding_model = NULL, "
                    "embedding_status = 'PENDING', embedding_updated_at = NULL "
                    "WHERE resume_id = %s AND source_type = %s "
                    "AND source_id = %s AND chunk_index = %s",
                    (
                        chunk.chunk_content,
                        chunk.content_hash,
                        resume_id,
                        key[0],
                        key[1],
                        key[2],
                    ),
                )
                updated += 1
            else:
                cur.execute(
                    "INSERT INTO resume_chunks "
                    "(resume_id, source_type, source_id, chunk_index, chunk_content, content_hash) "
                    "VALUES (%s, %s, %s, %s, %s, %s)",
                    (
                        resume_id,
                        key[0],
                        key[1],
                        key[2],
                        chunk.chunk_content,
                        chunk.content_hash,
                    ),
                )
                inserted += 1

        for source_type, source_id, chunk_index in existing.keys() - desired.keys():
            cur.execute(
                "DELETE FROM resume_chunks WHERE resume_id = %s AND source_type = %s "
                "AND source_id = %s AND chunk_index = %s",
                (resume_id, source_type, source_id, chunk_index),
            )
            deleted += 1

    return SyncStats(inserted, updated, deleted, unchanged)


def sync_cover_letter_chunks(
    conn: Any,
    cover_letter_item_id: int,
    chunks: Iterable[CoverLetterChunk],
) -> SyncStats:
    """자기소개서 항목 하나의 전체 청크 스냅샷을 동기화한다."""
    desired_list = list(chunks)
    desired = {chunk.key: chunk for chunk in desired_list}
    if len(desired) != len(desired_list):
        raise ValueError("cover letter chunk index가 중복되었습니다.")
    if any(chunk.cover_letter_item_id != cover_letter_item_id for chunk in desired_list):
        raise ValueError("다른 cover_letter_item_id의 청크가 포함되어 있습니다.")

    inserted = updated = deleted = unchanged = 0
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM cover_letter_items WHERE id = %s FOR UPDATE",
            (cover_letter_item_id,),
        )
        if cur.fetchone() is None:
            raise ValueError(f"cover letter item을 찾을 수 없습니다: {cover_letter_item_id}")

        cur.execute(
            "SELECT chunk_index, content_hash FROM cover_letter_chunks "
            "WHERE cover_letter_item_id = %s",
            (cover_letter_item_id,),
        )
        existing = {
            int(index): hash_value
            for index, hash_value in (
                _values(row, ("chunk_index", "content_hash")) for row in cur.fetchall()
            )
        }

        for index, chunk in desired.items():
            previous_hash = existing.get(index)
            if previous_hash == chunk.content_hash:
                unchanged += 1
                continue
            if index in existing:
                cur.execute(
                    "UPDATE cover_letter_chunks SET chunk_content = %s, content_hash = %s, "
                    "embedding = NULL, embedding_model = NULL, "
                    "embedding_status = 'PENDING', embedding_updated_at = NULL "
                    "WHERE cover_letter_item_id = %s AND chunk_index = %s",
                    (
                        chunk.chunk_content,
                        chunk.content_hash,
                        cover_letter_item_id,
                        index,
                    ),
                )
                updated += 1
            else:
                cur.execute(
                    "INSERT INTO cover_letter_chunks "
                    "(cover_letter_item_id, chunk_index, chunk_content, content_hash) "
                    "VALUES (%s, %s, %s, %s)",
                    (
                        cover_letter_item_id,
                        index,
                        chunk.chunk_content,
                        chunk.content_hash,
                    ),
                )
                inserted += 1

        for index in existing.keys() - desired.keys():
            cur.execute(
                "DELETE FROM cover_letter_chunks "
                "WHERE cover_letter_item_id = %s AND chunk_index = %s",
                (cover_letter_item_id, index),
            )
            deleted += 1

    return SyncStats(inserted, updated, deleted, unchanged)

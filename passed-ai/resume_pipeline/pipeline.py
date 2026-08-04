"""사용자 한 명의 이력서·자기소개서 청크를 동기화하는 오케스트레이터."""

from __future__ import annotations

from typing import Any

from .chunk_sync import sync_cover_letter_chunks, sync_resume_chunks
from .cover_letter_chunker import build_cover_letter_chunks
from .models import ChunkingResult, ResumeSourceType, SyncStats
from .resume_text_builder import build_resume_chunks


_SOURCE_TABLES: tuple[tuple[ResumeSourceType, str], ...] = (
    (ResumeSourceType.EDUCATION, "educations"),
    (ResumeSourceType.EXPERIENCE, "experiences"),
    (ResumeSourceType.ACTIVITY, "activities"),
    (ResumeSourceType.TRAINING, "trainings"),
    (ResumeSourceType.CERTIFICATION, "certifications"),
    (ResumeSourceType.AWARD, "awards"),
    (ResumeSourceType.OVERSEAS_EXPERIENCE, "overseas_experiences"),
    (ResumeSourceType.LANGUAGE, "language_proficiencies"),
)


def _sum_stats(items: list[SyncStats]) -> SyncStats:
    return SyncStats(
        inserted=sum(item.inserted for item in items),
        updated=sum(item.updated for item in items),
        deleted=sum(item.deleted for item in items),
        unchanged=sum(item.unchanged for item in items),
    )


def run_chunking_for_user(conn: Any, user_id: int) -> ChunkingResult:
    """현재 원본 테이블 전체를 기준으로 사용자의 파생 청크를 동기화한다."""
    resume_stats = SyncStats()
    cover_stats: list[SyncStats] = []

    with conn.cursor() as cur:
        cur.execute("SELECT id FROM resumes WHERE user_id = %s", (user_id,))
        resume_row = cur.fetchone()

    if resume_row:
        resume_id = int(resume_row["id"] if isinstance(resume_row, dict) else resume_row[0])
        all_chunks = []
        with conn.cursor() as cur:
            for source_type, table in _SOURCE_TABLES:
                cur.execute(f"SELECT * FROM {table} WHERE resume_id = %s ORDER BY id", (resume_id,))
                all_chunks.extend(build_resume_chunks(resume_id, source_type, cur.fetchall()))
        resume_stats = sync_resume_chunks(conn, resume_id, all_chunks)

    with conn.cursor() as cur:
        cur.execute(
            "SELECT cli.id, cli.answer FROM cover_letter_items cli "
            "JOIN cover_letters cl ON cl.id = cli.cover_letter_id "
            "WHERE cl.user_id = %s ORDER BY cli.id",
            (user_id,),
        )
        items = cur.fetchall()

    for item in items:
        item_id = int(item["id"] if isinstance(item, dict) else item[0])
        answer = item["answer"] if isinstance(item, dict) else item[1]
        chunks = build_cover_letter_chunks(item_id, answer)
        cover_stats.append(sync_cover_letter_chunks(conn, item_id, chunks))

    return ChunkingResult(resume_stats, _sum_stats(cover_stats))

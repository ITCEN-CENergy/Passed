"""사용자 한 명의 이력서·자기소개서 청크를 동기화하는 오케스트레이터."""

from __future__ import annotations

import logging
from typing import Any

from .chunk_sync import sync_cover_letter_chunks, sync_resume_chunks
from .cover_letter_chunker import build_cover_letter_chunks
from .models import ChunkingResult, ResumeSourceType, SyncStats
from .resume_text_builder import build_resume_chunks


logger = logging.getLogger(__name__)


class MissingResumeError(ValueError):
    """필수 입력인 이력서가 없는 사용자를 처리하려 할 때 발생한다."""


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
    cover_stats: list[SyncStats] = []

    with conn.cursor() as cur:
        cur.execute("SELECT id FROM resumes WHERE user_id = %s", (user_id,))
        resume_row = cur.fetchone()

    if resume_row is None:
        raise MissingResumeError(
            f"필수 이력서를 찾을 수 없습니다: user_id={user_id}. "
            "resumes.user_id와 현재 DATABASE_URL을 확인하세요."
        )

    resume_id = int(resume_row["id"] if isinstance(resume_row, dict) else resume_row[0])
    all_chunks = []
    with conn.cursor() as cur:
        for source_type, table in _SOURCE_TABLES:
            cur.execute(f"SELECT * FROM {table} WHERE resume_id = %s ORDER BY id", (resume_id,))
            rows = cur.fetchall()
            source_chunks = build_resume_chunks(resume_id, source_type, rows)
            all_chunks.extend(source_chunks)

            logger.info(
                "이력서 원본 조회 user_id=%s resume_id=%s table=%s rows=%s chunks=%s",
                user_id,
                resume_id,
                table,
                len(rows),
                len(source_chunks),
            )
    resume_stats = sync_resume_chunks(conn, resume_id, all_chunks)

    with conn.cursor() as cur:
        cur.execute(
            "SELECT cli.id, cli.answer FROM cover_letter_items cli "
            "JOIN cover_letters cl ON cl.id = cli.cover_letter_id "
            "WHERE cl.user_id = %s ORDER BY cli.id",
            (user_id,),
        )
        items = cur.fetchall()

    logger.info("자기소개서 원본 조회 user_id=%s items=%s", user_id, len(items))

    for item in items:
        item_id = int(item["id"] if isinstance(item, dict) else item[0])
        answer = item["answer"] if isinstance(item, dict) else item[1]
        chunks = build_cover_letter_chunks(item_id, answer)
        logger.info(
            "자기소개서 청킹 user_id=%s item_id=%s answer_chars=%s chunks=%s",
            user_id,
            item_id,
            len(answer or ""),
            len(chunks),
        )
        cover_stats.append(sync_cover_letter_chunks(conn, item_id, chunks))

    return ChunkingResult(resume_stats, _sum_stats(cover_stats))

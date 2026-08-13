from __future__ import annotations

import os

from resume_pipeline.db import connection, validate_embedding_schema
from resume_pipeline.embedding_worker import (
    COVER_LETTER_USER_FILTER_SQL,
    EMBEDDING_BATCH_SIZE,
    RESUME_USER_FILTER_SQL,
    embed_pending_chunks,
)
from resume_pipeline.pipeline import run_chunking_for_user
from resume_pipeline.skill_extraction_worker import extract_user_skill_candidates
from resume_pipeline.user_skill_analysis_state import (
    build_analysis_fingerprint,
    load_reusable_analysis_state,
    save_analysis_state,
)
from resume_pipeline.user_skill_mapping_worker import (
    build_user_skill_mapping_report,
    persist_user_skill_mapping,
)

from .schema import UserSkillExtractionResponse


class UserSkillPipelineConfigurationError(RuntimeError):
    """필수 환경 설정이 없어 분석을 시작할 수 없을 때 발생한다."""


class UserSkillPipelineExecutionError(RuntimeError):
    """일부 청크 처리 실패로 안전한 최종 저장을 할 수 없을 때 발생한다."""


def _retry_failed_embeddings(conn: object, user_id: int) -> None:
    """이전 요청에서 실패한 사용자의 청크를 명시적인 재요청 때 다시 시도한다."""
    with conn.cursor() as cur:  # type: ignore[attr-defined]
        cur.execute(
            "UPDATE resume_chunks SET embedding_status = 'PENDING' "
            "WHERE embedding_status = 'FAILED' AND resume_id IN ("
            "SELECT id FROM resumes WHERE user_id = %s)",
            (user_id,),
        )
        cur.execute(
            "UPDATE cover_letter_chunks SET embedding_status = 'PENDING' "
            "WHERE embedding_status = 'FAILED' AND cover_letter_item_id IN ("
            "SELECT ci.id FROM cover_letter_items ci "
            "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
            "WHERE cl.user_id = %s)",
            (user_id,),
        )


def run_user_skill_analysis(user_id: int) -> UserSkillExtractionResponse:
    """한 사용자의 청킹부터 최종 사용자 스킬 저장까지 동기 실행한다."""
    with connection() as conn:
        validate_embedding_schema(conn)
        run_chunking_for_user(conn, user_id)
        fingerprint = build_analysis_fingerprint(conn, user_id)
        cached = load_reusable_analysis_state(conn, user_id, fingerprint)
        if cached is not None:
            return UserSkillExtractionResponse(
                user_id=user_id,
                processed_chunk_count=cached.processed_chunk_count,
                skill_count=cached.skill_count,
                unmapped_count=cached.unmapped_count,
                persisted=True,
                resume_chunks_embedded=0,
                cover_letter_chunks_embedded=0,
            )

        if not os.getenv("OPENAI_API_KEY"):
            raise UserSkillPipelineConfigurationError("OPENAI_API_KEY is required")

        _retry_failed_embeddings(conn, user_id)

        resume_stats = embed_pending_chunks(
            conn,
            "resume_chunks",
            filter_sql=RESUME_USER_FILTER_SQL,
            filter_params=(user_id,),
            batch_size=EMBEDDING_BATCH_SIZE,
        )
        cover_stats = embed_pending_chunks(
            conn,
            "cover_letter_chunks",
            filter_sql=COVER_LETTER_USER_FILTER_SQL,
            filter_params=(user_id,),
            batch_size=EMBEDDING_BATCH_SIZE,
        )
        if resume_stats.failed or cover_stats.failed:
            raise UserSkillPipelineExecutionError("chunk embedding failed")

        extraction = extract_user_skill_candidates(conn, user_id)
        if extraction.failures:
            raise UserSkillPipelineExecutionError("skill extraction failed")

        report = build_user_skill_mapping_report(conn, extraction)
        persist_user_skill_mapping(conn, report)
        save_analysis_state(
            conn,
            user_id,
            fingerprint,
            skill_count=len(report.skills),
            unmapped_count=len(report.unmapped),
        )

        return UserSkillExtractionResponse(
            user_id=user_id,
            processed_chunk_count=report.processed_chunk_count,
            skill_count=len(report.skills),
            unmapped_count=len(report.unmapped),
            persisted=True,
            resume_chunks_embedded=resume_stats.embedded,
            cover_letter_chunks_embedded=cover_stats.embedded,
        )

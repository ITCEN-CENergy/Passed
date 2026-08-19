from __future__ import annotations

import logging
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
from resume_pipeline.skill_recall_worker import (
    build_pass1_strict_validation_retrieval,
    filter_pass1_mapping_with_strict_validation,
    retrieve_missing_master_candidates,
    verify_retrieval_with_pass2,
)
from resume_pipeline.user_skill_analysis_state import (
    build_analysis_fingerprint,
    load_reusable_analysis_state,
    save_analysis_state,
)
from resume_pipeline.user_skill_mapping_worker import (
    build_user_skill_mapping_report,
    merge_verified_pass2_skills,
    persist_user_skill_mapping,
)

from .schema import UserSkillExtractionResponse


logger = logging.getLogger(__name__)
RUNTIME_RETRIEVAL_TOP_K = 40
RUNTIME_SENTENCE_TOP_K = 5

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

        pass1_mapping = build_user_skill_mapping_report(conn, extraction)

        # Q. Pass 1 오탐을 그대로 저장하지 않으면서 Recall은 어떻게 보완하나요?
        # A. 먼저 매핑된 Pass 1 기술·성향을 같은 Strict 계약으로 재검증합니다. 그 뒤
        #    승인된 마스터를 제외한 Hybrid Top-40만 Pass 2에 제시해 자유 생성을 막습니다.
        pass1_validation_input = build_pass1_strict_validation_retrieval(
            conn,
            extraction,
            pass1_mapping,
        )
        pass1_validation = verify_retrieval_with_pass2(
            pass1_validation_input,
            strict=True,
        )
        strict_pass1_mapping = filter_pass1_mapping_with_strict_validation(
            pass1_mapping,
            pass1_validation,
        )

        retrieval = retrieve_missing_master_candidates(
            conn,
            extraction,
            strict_pass1_mapping,
            top_k_per_category=RUNTIME_RETRIEVAL_TOP_K,
            retrieval_mode="hybrid",
            sentence_top_k=RUNTIME_SENTENCE_TOP_K,
            final_top_k=RUNTIME_RETRIEVAL_TOP_K,
        )
        pass2 = verify_retrieval_with_pass2(
            retrieval,
            strict=True,
            pass1_mapping=strict_pass1_mapping,
        )
        report = merge_verified_pass2_skills(
            extraction,
            strict_pass1_mapping,
            pass2,
        )
        logger.info(
            "사용자 스킬 Strict 파이프라인 완료 user_id=%s "
            "pass1_skills=%s pass2_recovered=%s final_skills=%s unmapped=%s",
            user_id,
            len(strict_pass1_mapping.skills),
            pass2.verified_count,
            len(report.skills),
            len(report.unmapped),
        )
        for item in report.unmapped:
            logger.info(
                "unmapped_skill_candidate user_id=%s source=%s chunk_id=%s "
                "name=%r category=%s reason=%s evidence=%r",
                user_id,
                item.source_kind,
                item.chunk_id,
                item.extracted_name,
                item.category.value,
                item.failure_reason.value,
                item.evidence,
            )
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

"""변경 없는 사용자 문서의 스킬 분석 결과를 재사용하기 위한 최신 상태 관리."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from typing import Any

from .embedding_worker import EMBEDDING_MODEL
from .skill_extraction_prompt import SYSTEM_PROMPT
from .skill_extraction_worker import (
    SKILL_EXTRACTION_MODEL,
    _EXPLICIT_COMPLETED_SKILL_RULES,
)
from .user_skill_mapping_worker import (
    MIN_EMBEDDING_MARGIN,
    MIN_EMBEDDING_SIMILARITY,
)


@dataclass(frozen=True)
class AnalysisFingerprint:
    document_hash: str
    pipeline_hash: str
    processed_chunk_count: int


@dataclass(frozen=True)
class ReusableAnalysisState:
    processed_chunk_count: int
    skill_count: int
    unmapped_count: int


def _values(row: Any, names: tuple[str, ...]) -> tuple[Any, ...]:
    if isinstance(row, dict):
        return tuple(row[name] for name in names)
    return tuple(row)


def _sha256(payload: Any) -> str:
    serialized = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


def build_analysis_fingerprint(conn: Any, user_id: int) -> AnalysisFingerprint:
    """현재 청크와 추출·매핑 설정을 각각 결정적인 SHA-256 값으로 만든다."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT source_kind, chunk_id, context_type, content_hash FROM ("
            "SELECT 'RESUME' AS source_kind, rc.id AS chunk_id, "
            "rc.source_type AS context_type, rc.content_hash "
            "FROM resume_chunks rc JOIN resumes r ON r.id = rc.resume_id "
            "WHERE r.user_id = %s "
            "UNION ALL "
            "SELECT 'COVER_LETTER' AS source_kind, cc.id AS chunk_id, "
            "q.question_type AS context_type, cc.content_hash "
            "FROM cover_letter_chunks cc "
            "JOIN cover_letter_items ci ON ci.id = cc.cover_letter_item_id "
            "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
            "JOIN cover_letter_questions q ON q.id = ci.question_id "
            "WHERE cl.user_id = %s"
            ") chunks ORDER BY source_kind, chunk_id",
            (user_id, user_id),
        )
        chunk_rows = [
            _values(
                row,
                ("source_kind", "chunk_id", "context_type", "content_hash"),
            )
            for row in cur.fetchall()
        ]

        cur.execute(
            "SELECT id, name, category FROM skills ORDER BY id"
        )
        skill_rows = [
            _values(row, ("id", "name", "category")) for row in cur.fetchall()
        ]

        cur.execute(
            "SELECT skill_id, normalized_alias FROM skill_aliases "
            "ORDER BY skill_id, normalized_alias"
        )
        alias_rows = [
            _values(row, ("skill_id", "normalized_alias"))
            for row in cur.fetchall()
        ]

    rule_rows = [
        (name, category.value, pattern.pattern, pattern.flags)
        for name, category, pattern in _EXPLICIT_COMPLETED_SKILL_RULES
    ]
    document_hash = _sha256(chunk_rows)
    pipeline_hash = _sha256(
        {
            "extraction_model": SKILL_EXTRACTION_MODEL,
            "embedding_model": EMBEDDING_MODEL,
            "system_prompt": SYSTEM_PROMPT,
            "explicit_rules": rule_rows,
            "min_embedding_similarity": MIN_EMBEDDING_SIMILARITY,
            "min_embedding_margin": MIN_EMBEDDING_MARGIN,
            "skills": skill_rows,
            "aliases": alias_rows,
        }
    )
    return AnalysisFingerprint(
        document_hash=document_hash,
        pipeline_hash=pipeline_hash,
        processed_chunk_count=len(chunk_rows),
    )


def load_reusable_analysis_state(
    conn: Any,
    user_id: int,
    fingerprint: AnalysisFingerprint,
) -> ReusableAnalysisState | None:
    """해시와 저장 무결성이 모두 일치할 때만 마지막 성공 결과를 반환한다."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT state.processed_chunk_count, state.skill_count, "
            "state.unmapped_count, "
            "(SELECT COUNT(*) FROM user_skills us WHERE us.user_id = state.user_id) "
            "AS actual_skill_count, "
            "EXISTS ("
            "SELECT 1 FROM user_skills us WHERE us.user_id = state.user_id "
            "AND NOT EXISTS (SELECT 1 FROM user_skill_evidences usev "
            "WHERE usev.user_skill_id = us.id)"
            ") AS has_skill_without_evidence "
            "FROM user_skill_analysis_states state "
            "WHERE state.user_id = %s AND state.document_hash = %s "
            "AND state.pipeline_hash = %s",
            (user_id, fingerprint.document_hash, fingerprint.pipeline_hash),
        )
        row = cur.fetchone()

    if row is None:
        return None
    (
        processed_chunk_count,
        skill_count,
        unmapped_count,
        actual_skill_count,
        has_skill_without_evidence,
    ) = _values(
        row,
        (
            "processed_chunk_count",
            "skill_count",
            "unmapped_count",
            "actual_skill_count",
            "has_skill_without_evidence",
        ),
    )
    if (
        int(processed_chunk_count) != fingerprint.processed_chunk_count
        or int(skill_count) <= 0
        or int(actual_skill_count) != int(skill_count)
        or bool(has_skill_without_evidence)
    ):
        return None
    return ReusableAnalysisState(
        processed_chunk_count=int(processed_chunk_count),
        skill_count=int(skill_count),
        unmapped_count=int(unmapped_count),
    )


def save_analysis_state(
    conn: Any,
    user_id: int,
    fingerprint: AnalysisFingerprint,
    *,
    skill_count: int,
    unmapped_count: int,
) -> None:
    """성공적으로 저장된 분석의 최신 상태 한 건만 upsert한다."""
    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO user_skill_analysis_states "
            "(user_id, document_hash, pipeline_hash, processed_chunk_count, "
            "skill_count, unmapped_count) VALUES (%s, %s, %s, %s, %s, %s) "
            "ON CONFLICT (user_id) DO UPDATE SET "
            "document_hash = EXCLUDED.document_hash, "
            "pipeline_hash = EXCLUDED.pipeline_hash, "
            "processed_chunk_count = EXCLUDED.processed_chunk_count, "
            "skill_count = EXCLUDED.skill_count, "
            "unmapped_count = EXCLUDED.unmapped_count, "
            "updated_at = CURRENT_TIMESTAMP",
            (
                user_id,
                fingerprint.document_hash,
                fingerprint.pipeline_hash,
                fingerprint.processed_chunk_count,
                skill_count,
                unmapped_count,
            ),
        )

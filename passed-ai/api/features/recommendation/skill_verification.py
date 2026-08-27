from __future__ import annotations

from collections import defaultdict
from collections.abc import Awaitable, Callable
import logging
import re
import unicodedata
from typing import Protocol

from resume_pipeline.db import connection

from api.features.recommendation.client import (
    OpenAiRecommendationSkillVerificationClient,
)
from api.features.recommendation.config import get_recommendation_settings
from api.features.recommendation.schema import (
    DirectSkillEvidence,
    DirectSkillVerificationCandidate,
    DirectSkillVerificationModelResponse,
    SkillEvidence,
    SkillVerificationCandidate,
    SkillVerificationModelResponse,
    SkillVerificationRequest,
    SkillVerificationResponse,
    VerifiedSkillMatch,
)


_MIN_SKILL_SIMILARITY = 0.55
_SOURCE_SKILL_LIMIT = 2
_EVIDENCE_LIMIT = 3
_DIRECT_EVIDENCE_LIMIT = 2
_MIN_DIRECT_EVIDENCE_SIMILARITY = 0.25
_CERTIFICATION_CATEGORY = "CERTIFICATION"

_GENERIC_TARGET_TOKENS = {
    "경험",
    "관련",
    "기술",
    "능력",
    "업무",
    "역량",
    "프로젝트",
}
_COMPLETED_ACTION_PATTERN = re.compile(
    r"개발|구현|적용|분석|운영|설계|구축|수정|개선|문서화|모니터링|연동|"
    r"처리|수행|관리|제어|차단|특정|평가|테스트|배포|활용|사용|준수|수립"
)
_ADVANCED_ACTION_PATTERN = re.compile(
    r"설계|최적화|주도|리딩|아키텍처|자동화|고도화|병목|근본 원인"
)
_MEASURABLE_RESULT_PATTERN = re.compile(
    r"\d+(?:\.\d+)?\s*(?:%|퍼센트|배|초|분|시간|건|명|회)|"
    r"감소|향상|개선|단축|절감|증가|해결|방지"
)
_CERTIFICATION_ACTION_PATTERN = re.compile(
    r"취득|합격|보유|자격증|자격\s*취득|certified|certification",
    re.IGNORECASE,
)
_SCOPE_CONSTRAINTS: tuple[tuple[str, re.Pattern[str]], ...] = (
    (
        "음성",
        re.compile(
            r"음성|오디오|화자|발화|음향|speech|audio|voice|\b(?:asr|stt|tts)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "설계",
        re.compile(r"설계|구조화|아키텍처|흐름|규칙|정책|기준|예외 조건|수립"),
    ),
    (
        "원인",
        re.compile(r"원인|근본 원인|root\s*cause|재현 테스트", re.IGNORECASE),
    ),
    (
        "분석",
        re.compile(r"분석|조사|비교|해석|원인을?\s*특정|패턴을?\s*도출"),
    ),
    (
        "보안",
        re.compile(
            r"보안|보호|개인정보|기밀|권한|접근\s*제어|필터|차단|유출|위협|취약점"
        ),
    ),
)


logger = logging.getLogger(__name__)


class SkillVerificationGenerator(Protocol):
    def verify(
        self,
        candidates: list[SkillVerificationCandidate],
    ) -> Awaitable[SkillVerificationModelResponse]: ...

    def verify_direct(
        self,
        candidates: list[DirectSkillVerificationCandidate],
    ) -> Awaitable[DirectSkillVerificationModelResponse]: ...


_CANDIDATE_SQL = """
    WITH ranked_sources AS (
        SELECT
            target.id AS target_skill_id,
            target.name AS target_skill_name,
            target.category AS target_skill_category,
            target.description AS target_skill_description,
            source.id AS source_skill_id,
            source.name AS source_skill_name,
            source.category AS source_skill_category,
            source.description AS source_skill_description,
            user_skill.id AS user_skill_id,
            1 - (target.embedding <=> source.embedding) AS similarity,
            ROW_NUMBER() OVER (
                PARTITION BY target.id
                ORDER BY target.embedding <=> source.embedding, source.id
            ) AS source_rank
        FROM skills target
        JOIN user_skills user_skill ON user_skill.user_id = %s
        JOIN skills source ON source.id = user_skill.skill_id
        WHERE target.id = ANY(%s)
          AND target.id <> source.id
          AND target.embedding IS NOT NULL
          AND source.embedding IS NOT NULL
          AND target.description IS NOT NULL
          AND source.description IS NOT NULL
    )
    SELECT
        ranked.target_skill_id,
        ranked.target_skill_name,
        ranked.target_skill_category,
        ranked.target_skill_description,
        ranked.source_skill_id,
        ranked.source_skill_name,
        ranked.source_skill_category,
        ranked.source_skill_description,
        ranked.similarity,
        evidence.id AS evidence_id,
        evidence.document_text AS evidence_text,
        evidence.extracted_level
    FROM ranked_sources ranked
    JOIN LATERAL (
        SELECT
            user_evidence.id,
            COALESCE(
                resume_chunk.chunk_content,
                cover_letter_chunk.chunk_content,
                user_evidence.evidence_text
            ) AS document_text,
            user_evidence.extracted_level
        FROM user_skill_evidences user_evidence
        LEFT JOIN resume_chunks resume_chunk
            ON resume_chunk.id = user_evidence.resume_chunk_id
        LEFT JOIN cover_letter_chunks cover_letter_chunk
            ON cover_letter_chunk.id = user_evidence.cover_letter_chunk_id
        WHERE user_evidence.user_skill_id = ranked.user_skill_id
        ORDER BY user_evidence.updated_at DESC, user_evidence.id DESC
        LIMIT %s
    ) evidence ON TRUE
    WHERE ranked.source_rank <= %s
      AND ranked.similarity >= %s
    ORDER BY ranked.target_skill_id, ranked.source_rank, evidence.id
"""


_DIRECT_CANDIDATE_SQL = """
    WITH target_skills AS (
        SELECT id, name, category, description, embedding
        FROM skills
        WHERE id = ANY(%s)
          AND embedding IS NOT NULL
          AND description IS NOT NULL
    ), document_chunks AS (
        SELECT
            'RESUME'::text AS source_kind,
            resume_chunk.id AS chunk_id,
            resume_chunk.source_type AS context_type,
            resume_chunk.chunk_content AS chunk_text,
            resume_chunk.embedding
        FROM resume_chunks resume_chunk
        JOIN resumes resume ON resume.id = resume_chunk.resume_id
        WHERE resume.user_id = %s
          AND resume_chunk.embedding_status = 'COMPLETED'
          AND resume_chunk.embedding IS NOT NULL

        UNION ALL

        SELECT
            'COVER_LETTER'::text AS source_kind,
            cover_chunk.id AS chunk_id,
            question.question_type AS context_type,
            cover_chunk.chunk_content AS chunk_text,
            cover_chunk.embedding
        FROM cover_letter_chunks cover_chunk
        JOIN cover_letter_items item ON item.id = cover_chunk.cover_letter_item_id
        JOIN cover_letters cover_letter ON cover_letter.id = item.cover_letter_id
        JOIN cover_letter_questions question ON question.id = item.question_id
        WHERE cover_letter.user_id = %s
          AND cover_chunk.embedding_status = 'COMPLETED'
          AND cover_chunk.embedding IS NOT NULL
    ), ranked AS (
        SELECT
            target.id AS target_skill_id,
            target.name AS target_skill_name,
            target.category AS target_skill_category,
            target.description AS target_skill_description,
            document.source_kind,
            document.chunk_id,
            document.context_type,
            document.chunk_text,
            GREATEST(0, 1 - (target.embedding <=> document.embedding)) AS similarity,
            ROW_NUMBER() OVER (
                PARTITION BY target.id
                ORDER BY target.embedding <=> document.embedding,
                         document.source_kind,
                         document.chunk_id
            ) AS evidence_rank
        FROM target_skills target
        JOIN document_chunks document ON (
            (
                target.category = 'CERTIFICATION'
                AND document.source_kind = 'RESUME'
                AND document.context_type = 'CERTIFICATION'
            )
            OR (
                target.category <> 'CERTIFICATION'
                AND NOT (
                    document.source_kind = 'RESUME'
                    AND document.context_type IN (
                        'CERTIFICATION', 'TRAINING', 'EDUCATION', 'LANGUAGE'
                    )
                )
            )
        )
    )
    SELECT *
    FROM ranked
    WHERE evidence_rank <= %s
      AND similarity >= %s
    ORDER BY target_skill_id, evidence_rank
"""


def load_skill_verification_candidates(
    user_id: int,
    target_skill_ids: list[int],
) -> list[SkillVerificationCandidate]:
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                _CANDIDATE_SQL,
                (
                    user_id,
                    target_skill_ids,
                    _EVIDENCE_LIMIT,
                    _SOURCE_SKILL_LIMIT,
                    _MIN_SKILL_SIMILARITY,
                ),
            )
            rows = cur.fetchall()

    grouped: dict[tuple[int, int], list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        grouped[(int(row["target_skill_id"]), int(row["source_skill_id"]))].append(row)

    candidates: list[SkillVerificationCandidate] = []
    for pair_rows in grouped.values():
        first = pair_rows[0]
        candidates.append(
            SkillVerificationCandidate(
                targetSkillId=int(first["target_skill_id"]),
                targetSkillName=str(first["target_skill_name"]),
                targetSkillCategory=str(first["target_skill_category"]),
                targetSkillDescription=str(first["target_skill_description"]),
                sourceSkillId=int(first["source_skill_id"]),
                sourceSkillName=str(first["source_skill_name"]),
                sourceSkillCategory=str(first["source_skill_category"]),
                sourceSkillDescription=str(first["source_skill_description"]),
                similarity=float(first["similarity"]),
                evidences=[
                    SkillEvidence(
                        evidenceId=int(row["evidence_id"]),
                        text=str(row["evidence_text"])[:4000],
                        extractedLevel=int(row["extracted_level"]),
                    )
                    for row in pair_rows
                ],
            )
        )
    return candidates


def load_direct_skill_verification_candidates(
    user_id: int,
    target_skill_ids: list[int],
) -> list[DirectSkillVerificationCandidate]:
    if not target_skill_ids:
        return []
    with connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                _DIRECT_CANDIDATE_SQL,
                (
                    target_skill_ids,
                    user_id,
                    user_id,
                    _DIRECT_EVIDENCE_LIMIT,
                    _MIN_DIRECT_EVIDENCE_SIMILARITY,
                ),
            )
            rows = cur.fetchall()

    grouped: dict[int, list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        grouped[int(row["target_skill_id"])].append(row)

    candidates: list[DirectSkillVerificationCandidate] = []
    for target_rows in grouped.values():
        first = target_rows[0]
        candidates.append(
            DirectSkillVerificationCandidate(
                targetSkillId=int(first["target_skill_id"]),
                targetSkillName=str(first["target_skill_name"]),
                targetSkillCategory=str(first["target_skill_category"]),
                targetSkillDescription=str(first["target_skill_description"]),
                evidences=[
                    DirectSkillEvidence(
                        sourceKind=str(row["source_kind"]),
                        chunkId=int(row["chunk_id"]),
                        contextType=str(row["context_type"]),
                        text=str(row["chunk_text"])[:4000],
                        similarity=float(row["similarity"]),
                    )
                    for row in target_rows
                ],
            )
        )
    return candidates


async def verify_recommendation_skills(
    request: SkillVerificationRequest,
    generator: SkillVerificationGenerator | None = None,
    loader: Callable[[int, list[int]], list[SkillVerificationCandidate]] = (
        load_skill_verification_candidates
    ),
    direct_loader: Callable[
        [int, list[int]], list[DirectSkillVerificationCandidate]
    ] | None = None,
) -> SkillVerificationResponse:
    candidates = loader(request.userId, request.targetSkillIds)
    use_direct_fallback = direct_loader is not None or (
        loader is load_skill_verification_candidates
    )
    selected_generator: SkillVerificationGenerator | None = generator

    verified: list[VerifiedSkillMatch] = []
    if candidates:
        selected_generator = selected_generator or (
            OpenAiRecommendationSkillVerificationClient(
                get_recommendation_settings()
            )
        )
        generated = await selected_generator.verify(candidates)
        verified = _validate_and_materialize(candidates, generated)

    if not use_direct_fallback:
        return SkillVerificationResponse(verifiedSkills=verified)

    verified_target_ids = {skill.targetSkillId for skill in verified}
    missing_target_ids = [
        target_id
        for target_id in request.targetSkillIds
        if target_id not in verified_target_ids
    ]
    if not missing_target_ids:
        return SkillVerificationResponse(verifiedSkills=verified)

    resolved_direct_loader = direct_loader or load_direct_skill_verification_candidates
    direct_candidates = resolved_direct_loader(request.userId, missing_target_ids)
    if not direct_candidates:
        return SkillVerificationResponse(verifiedSkills=verified)

    selected_generator = selected_generator or (
        OpenAiRecommendationSkillVerificationClient(get_recommendation_settings())
    )
    direct_generated = await selected_generator.verify_direct(direct_candidates)
    verified.extend(
        _validate_and_materialize_direct(direct_candidates, direct_generated)
    )
    return SkillVerificationResponse(verifiedSkills=verified)


def _validate_and_materialize(
    candidates: list[SkillVerificationCandidate],
    generated: SkillVerificationModelResponse,
) -> list[VerifiedSkillMatch]:
    allowed: dict[tuple[int, int, int], tuple[SkillVerificationCandidate, SkillEvidence]] = {}
    for candidate in candidates:
        for evidence in candidate.evidences:
            allowed[
                (
                    candidate.targetSkillId,
                    candidate.sourceSkillId,
                    evidence.evidenceId,
                )
            ] = (candidate, evidence)

    verified_targets: set[int] = set()
    verified: list[VerifiedSkillMatch] = []
    for selection in generated.verified:
        if selection.targetSkillId in verified_targets:
            logger.warning(
                "Ignoring duplicated targetSkillId from skill verifier: %s",
                selection.targetSkillId,
            )
            continue
        matched = allowed.get(
            (
                selection.targetSkillId,
                selection.sourceSkillId,
                selection.evidenceId,
            )
        )
        if matched is None:
            logger.warning(
                "Ignoring ungrounded skill mapping from verifier: target=%s source=%s evidence=%s",
                selection.targetSkillId,
                selection.sourceSkillId,
                selection.evidenceId,
            )
            continue
        candidate, evidence = matched
        if not _categories_compatible(
            candidate.targetSkillCategory,
            candidate.sourceSkillCategory,
        ):
            logger.warning(
                "Ignoring category-incompatible skill mapping: "
                "target=%s(%s:%s) source=%s(%s:%s) evidence=%s",
                candidate.targetSkillId,
                candidate.targetSkillName,
                candidate.targetSkillCategory,
                candidate.sourceSkillId,
                candidate.sourceSkillName,
                candidate.sourceSkillCategory,
                evidence.evidenceId,
            )
            continue
        grounded_quote = _grounded_quote(selection.evidenceQuote, evidence.text)
        if grounded_quote is None:
            logger.warning(
                "Ignoring skill mapping with an ungrounded evidence quote: target=%s evidence=%s",
                selection.targetSkillId,
                selection.evidenceId,
            )
            continue
        if not _passes_scope_constraints(candidate.targetSkillName, evidence.text):
            logger.warning(
                "Ignoring semantically unsupported skill mapping: "
                "target=%s(%s) source=%s(%s) evidence=%s",
                candidate.targetSkillId,
                candidate.targetSkillName,
                candidate.sourceSkillId,
                candidate.sourceSkillName,
                evidence.evidenceId,
            )
            continue
        verified_targets.add(selection.targetSkillId)
        verified.append(
            VerifiedSkillMatch(
                targetSkillId=candidate.targetSkillId,
                targetSkillName=candidate.targetSkillName,
                sourceSkillId=candidate.sourceSkillId,
                sourceSkillName=candidate.sourceSkillName,
                inferredLevel=_target_specific_level(
                    candidate.targetSkillCategory,
                    grounded_quote,
                ),
                evidence=grounded_quote,
                similarity=candidate.similarity,
                relationship=selection.relationship,
            )
        )

    for candidate in candidates:
        if candidate.targetSkillId in verified_targets:
            continue
        recovered = _recover_direct_evidence(candidate)
        if recovered is None:
            continue
        evidence, quote = recovered
        verified_targets.add(candidate.targetSkillId)
        verified.append(
            VerifiedSkillMatch(
                targetSkillId=candidate.targetSkillId,
                targetSkillName=candidate.targetSkillName,
                sourceSkillId=candidate.sourceSkillId,
                sourceSkillName=candidate.sourceSkillName,
                inferredLevel=_target_specific_level(
                    candidate.targetSkillCategory,
                    quote,
                ),
                evidence=quote,
                similarity=candidate.similarity,
                relationship="TARGET_DIRECTLY_SUPPORTED",
            )
        )
        logger.info(
            "Recovered recommendation skill from explicit resume evidence: "
            "target=%s(%s) source=%s(%s) evidence=%s",
            candidate.targetSkillId,
            candidate.targetSkillName,
            candidate.sourceSkillId,
            candidate.sourceSkillName,
            evidence.evidenceId,
        )
    return verified


def _validate_and_materialize_direct(
    candidates: list[DirectSkillVerificationCandidate],
    generated: DirectSkillVerificationModelResponse,
) -> list[VerifiedSkillMatch]:
    allowed: dict[
        tuple[int, str, int],
        tuple[DirectSkillVerificationCandidate, DirectSkillEvidence],
    ] = {}
    for candidate in candidates:
        for evidence in candidate.evidences:
            allowed[
                (
                    candidate.targetSkillId,
                    evidence.sourceKind,
                    evidence.chunkId,
                )
            ] = (candidate, evidence)

    verified_targets: set[int] = set()
    verified: list[VerifiedSkillMatch] = []
    for selection in generated.verified:
        if selection.targetSkillId in verified_targets:
            logger.warning(
                "Ignoring duplicated direct targetSkillId from skill verifier: %s",
                selection.targetSkillId,
            )
            continue
        matched = allowed.get(
            (
                selection.targetSkillId,
                selection.sourceKind,
                selection.chunkId,
            )
        )
        if matched is None:
            logger.warning(
                "Ignoring ungrounded direct skill mapping: target=%s source=%s chunk=%s",
                selection.targetSkillId,
                selection.sourceKind,
                selection.chunkId,
            )
            continue

        candidate, evidence = matched
        grounded_quote = _grounded_quote(selection.evidenceQuote, evidence.text)
        if grounded_quote is None:
            logger.warning(
                "Ignoring direct skill mapping with an ungrounded quote: target=%s chunk=%s",
                selection.targetSkillId,
                selection.chunkId,
            )
            continue
        if not _direct_evidence_permitted(candidate, evidence, grounded_quote):
            logger.warning(
                "Ignoring unsupported direct document evidence: target=%s(%s) "
                "source=%s chunk=%s context=%s",
                candidate.targetSkillId,
                candidate.targetSkillName,
                evidence.sourceKind,
                evidence.chunkId,
                evidence.contextType,
            )
            continue

        verified_targets.add(candidate.targetSkillId)
        verified.append(
            VerifiedSkillMatch(
                targetSkillId=candidate.targetSkillId,
                targetSkillName=candidate.targetSkillName,
                sourceSkillId=None,
                sourceSkillName=None,
                inferredLevel=min(
                    selection.inferredLevel,
                    _target_specific_level(
                        candidate.targetSkillCategory,
                        grounded_quote,
                    ),
                ),
                evidence=grounded_quote,
                similarity=evidence.similarity,
                relationship="DIRECT_DOCUMENT_EVIDENCE",
            )
        )
        logger.info(
            "Verified recommendation skill from direct document evidence: "
            "target=%s(%s) source=%s chunk=%s context=%s level=%s",
            candidate.targetSkillId,
            candidate.targetSkillName,
            evidence.sourceKind,
            evidence.chunkId,
            evidence.contextType,
            verified[-1].inferredLevel,
        )
    return verified


def _direct_evidence_permitted(
    candidate: DirectSkillVerificationCandidate,
    evidence: DirectSkillEvidence,
    grounded_quote: str,
) -> bool:
    if not _passes_scope_constraints(candidate.targetSkillName, grounded_quote):
        return False
    if candidate.targetSkillCategory == _CERTIFICATION_CATEGORY:
        return (
            evidence.sourceKind == "RESUME"
            and evidence.contextType == _CERTIFICATION_CATEGORY
            and _CERTIFICATION_ACTION_PATTERN.search(grounded_quote) is not None
        )
    if evidence.sourceKind == "RESUME" and evidence.contextType in {
        "CERTIFICATION",
        "TRAINING",
        "EDUCATION",
        "LANGUAGE",
    }:
        return False
    return _COMPLETED_ACTION_PATTERN.search(grounded_quote) is not None


def _target_specific_level(target_category: str, evidence_quote: str) -> int:
    if target_category == _CERTIFICATION_CATEGORY:
        return 1
    if (
        _ADVANCED_ACTION_PATTERN.search(evidence_quote)
        and _MEASURABLE_RESULT_PATTERN.search(evidence_quote)
    ):
        return 3
    if _COMPLETED_ACTION_PATTERN.search(evidence_quote):
        return 2
    return 1


def _grounded_quote(quote: str, document: str) -> str | None:
    if quote in document:
        return quote
    normalized_quote = re.sub(r"\s+", " ", quote).strip()
    normalized_document = re.sub(r"\s+", " ", document).strip()
    if normalized_quote and normalized_quote in normalized_document:
        return normalized_quote
    return None


def _passes_scope_constraints(target_name: str, document: str) -> bool:
    normalized_target = _normalize_text(target_name)
    normalized_document = _normalize_text(document)
    for marker, evidence_pattern in _SCOPE_CONSTRAINTS:
        if marker in normalized_target and not evidence_pattern.search(normalized_document):
            return False
    return True


def _recover_direct_evidence(
    candidate: SkillVerificationCandidate,
) -> tuple[SkillEvidence, str] | None:
    if not _categories_compatible(
        candidate.targetSkillCategory,
        candidate.sourceSkillCategory,
    ):
        return None
    for evidence in candidate.evidences:
        quote = _direct_support_quote(candidate.targetSkillName, evidence.text)
        if quote is not None:
            return evidence, quote
    return None


def _categories_compatible(target_category: str, source_category: str) -> bool:
    target_is_certification = target_category == _CERTIFICATION_CATEGORY
    source_is_certification = source_category == _CERTIFICATION_CATEGORY
    return target_is_certification == source_is_certification


def _direct_support_quote(target_name: str, document: str) -> str | None:
    if not _passes_scope_constraints(target_name, document):
        return None
    target_tokens = _meaningful_target_tokens(target_name)
    if not target_tokens:
        return None

    for segment in re.split(r"(?<=[.!?])\s+|[\r\n]+", document):
        compact_segment = _compact_text(segment)
        if not compact_segment or not _COMPLETED_ACTION_PATTERN.search(segment):
            continue
        if all(_compact_text(token) in compact_segment for token in target_tokens):
            return segment.strip()[:500]
    return None


def _meaningful_target_tokens(target_name: str) -> list[str]:
    return [
        token
        for token in re.findall(r"[0-9A-Za-z가-힣+#.]+", _normalize_text(target_name))
        if token not in _GENERIC_TARGET_TOKENS
    ]


def _normalize_text(value: str) -> str:
    return unicodedata.normalize("NFKC", value).lower()


def _compact_text(value: str) -> str:
    return re.sub(r"[^0-9a-z가-힣+#.]", "", _normalize_text(value))

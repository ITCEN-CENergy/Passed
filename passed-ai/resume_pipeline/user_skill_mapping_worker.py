"""실제 사용자 추출 후보를 마스터에 연결하고 근거·level을 동기화한다."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
import re
import unicodedata
from typing import Any

from .embedding_worker import _create_embeddings, _create_openai_client
from .skill_extraction_models import SkillCategory, SkillExtractionReport
from .skill_mapping_models import MappingMethod
from .skill_mapping_name_only import embed_in_batches
from .skill_mapping_worker import (
    RawMappingResult,
    SkillAlias,
    SkillMaster,
    _similarity_rows,
    apply_mapping_thresholds,
    load_skill_aliases,
    load_skill_masters,
    resolve_alias,
    resolve_exact_or_normalized,
)
from .user_skill_mapping_models import (
    AggregatedUserSkill,
    MappedEvidence,
    PersistStats,
    ProcessedChunkRef,
    UnmappedEvidence,
    UserSkillMappingReport,
)


MIN_EMBEDDING_SIMILARITY = 0.75
MIN_EMBEDDING_MARGIN = 0.05


@dataclass(frozen=True)
class RuntimeMappingSubject:
    extracted_name: str
    extracted_category: SkillCategory


@dataclass(frozen=True)
class PendingCandidate:
    case_id: str
    source_kind: str
    chunk_id: int
    context_type: str
    content_hash: str
    extracted_name: str
    category: SkillCategory
    level: int
    evidence: str

    @property
    def subject(self) -> RuntimeMappingSubject:
        return RuntimeMappingSubject(self.extracted_name, self.category)


_EVIDENCE_SPACES = re.compile(r"\s+")


def _normalize_evidence(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold().strip()
    return _EVIDENCE_SPACES.sub(" ", normalized).rstrip(".!?。！？」\"")


def _method_confidence(method: MappingMethod, similarity: float | None) -> float:
    if method is MappingMethod.EXACT:
        return 1.0
    if method is MappingMethod.NORMALIZED:
        return 0.99
    if method is MappingMethod.ALIAS:
        return 0.95
    return round(float(similarity or 0.0), 3)


def _flatten_candidates(extraction: SkillExtractionReport) -> list[PendingCandidate]:
    flattened: list[PendingCandidate] = []
    for chunk in extraction.chunks:
        for index, candidate in enumerate(chunk.skills):
            flattened.append(
                PendingCandidate(
                    case_id=f"{chunk.source_kind}:{chunk.chunk_id}:{index}",
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    context_type=chunk.context_type,
                    content_hash=chunk.content_hash,
                    extracted_name=candidate.extracted_name,
                    category=candidate.category,
                    level=candidate.level,
                    evidence=candidate.evidence,
                )
            )
    return flattened


def _mapped_evidence(
    candidate: PendingCandidate,
    master: SkillMaster,
    method: MappingMethod,
    similarity: float | None = None,
) -> MappedEvidence:
    return MappedEvidence(
        skill_id=master.skill_id,
        skill_name=master.name,
        category=master.category,
        source_kind=candidate.source_kind,
        chunk_id=candidate.chunk_id,
        context_type=candidate.context_type,
        content_hash=candidate.content_hash,
        extracted_name=candidate.extracted_name,
        evidence=candidate.evidence,
        extracted_level=candidate.level,
        mapping_method=method,
        mapping_similarity=similarity,
        mapping_confidence=_method_confidence(method, similarity),
    )


def map_extracted_candidates(
    conn: Any,
    extraction: SkillExtractionReport,
    *,
    embedding_client: Any | None = None,
) -> tuple[list[MappedEvidence], list[UnmappedEvidence]]:
    """추출 후보를 결정적 규칙부터 처리하고 남은 후보에만 임베딩을 사용한다."""
    masters = load_skill_masters(conn)
    aliases = load_skill_aliases(conn)
    master_by_key = {(master.name, master.category): master for master in masters}
    mapped: list[MappedEvidence] = []
    unresolved: list[PendingCandidate] = []

    for candidate in _flatten_candidates(extraction):
        method, master, _ = resolve_exact_or_normalized(candidate.subject, masters)
        if method and master:
            mapped.append(_mapped_evidence(candidate, master, method))
            continue

        alias, _ = resolve_alias(candidate.subject, aliases)
        if alias:
            master = master_by_key[(alias.skill_name, alias.category)]
            mapped.append(
                _mapped_evidence(candidate, master, MappingMethod.ALIAS)
            )
            continue
        unresolved.append(candidate)

    unmapped: list[UnmappedEvidence] = []
    if not unresolved:
        return mapped, unmapped

    client = embedding_client or _create_openai_client()
    vectors = embed_in_batches(
        [candidate.extracted_name for candidate in unresolved],
        lambda texts: _create_embeddings(client, texts),
        batch_size=100,
    )
    for candidate, vector in zip(unresolved, vectors, strict=True):
        global_hits = _similarity_rows(conn, vector, category=None, limit=3)
        category_hits = _similarity_rows(
            conn, vector, category=candidate.category, limit=3
        )
        raw = RawMappingResult(
            case_id=candidate.case_id,
            expectation="MAP",
            extracted_name=candidate.extracted_name,
            extracted_category=candidate.category,
            global_top_k=global_hits,
            category_top_k=category_hits,
        )
        decision = apply_mapping_thresholds(
            raw,
            min_similarity=MIN_EMBEDDING_SIMILARITY,
            min_margin=MIN_EMBEDDING_MARGIN,
        )
        if decision.mapped and decision.skill_name:
            master = master_by_key[(decision.skill_name, candidate.category)]
            mapped.append(
                _mapped_evidence(
                    candidate,
                    master,
                    MappingMethod.EMBEDDING,
                    decision.similarity,
                )
            )
        else:
            unmapped.append(
                UnmappedEvidence(
                    source_kind=candidate.source_kind,
                    chunk_id=candidate.chunk_id,
                    context_type=candidate.context_type,
                    extracted_name=candidate.extracted_name,
                    category=candidate.category,
                    evidence=candidate.evidence,
                    extracted_level=candidate.level,
                    failure_reason=decision.failure_reason or "LOW_SIMILARITY",
                    category_top_k=category_hits,
                )
            )
    return mapped, unmapped


def _deduplicate_evidences(evidences: list[MappedEvidence]) -> list[MappedEvidence]:
    # Q. overlap 청크의 같은 문장을 근거 두 개로 세면 안 되나요?
    # A. 같은 행동을 두 번 센 것이므로 특히 BEHAVIORAL level이 부풀어 오릅니다.
    #    원문이 같은 근거와 동일 청크의 중복 매핑은 가장 신뢰도 높은 하나만 남깁니다.
    ordered = sorted(
        evidences,
        key=lambda item: (
            0 if item.source_kind == "RESUME" else 1,
            item.chunk_id,
            -item.mapping_confidence,
            -item.extracted_level,
        ),
    )
    result: list[MappedEvidence] = []
    seen_text: set[str] = set()
    seen_chunk: set[tuple[str, int]] = set()
    for evidence in ordered:
        text_key = _normalize_evidence(evidence.evidence)
        chunk_key = (evidence.source_kind, evidence.chunk_id)
        if text_key in seen_text or chunk_key in seen_chunk:
            continue
        seen_text.add(text_key)
        seen_chunk.add(chunk_key)
        result.append(evidence)
    return result


def _level_and_confidence(
    category: SkillCategory,
    evidences: list[MappedEvidence],
) -> tuple[int, float]:
    count = len(evidences)
    if category is SkillCategory.CERTIFICATION:
        return 1, 1.0
    if category is SkillCategory.BEHAVIORAL_TRAIT:
        level = min(3, count)
        return level, min(1.0, round(0.60 + 0.15 * count, 3))

    # Q. 기술을 여러 번 언급하면 level을 올리지 않나요?
    # A. 같은 기초 사용 경험이 반복됐다고 능숙해지는 것은 아닙니다. TECHNICAL과
    #    EXPERIENCE는 가장 깊은 근거의 level을 사용하고, 반복 근거는 신뢰도만 높입니다.
    level = max(evidence.extracted_level for evidence in evidences)
    source_bonus = 0.10 if len({item.source_kind for item in evidences}) > 1 else 0.0
    confidence = min(1.0, 0.65 + min(count, 3) * 0.08 + source_bonus)
    return level, round(confidence, 3)


def aggregate_mapped_evidences(
    mapped: list[MappedEvidence],
) -> list[AggregatedUserSkill]:
    grouped: dict[int, list[MappedEvidence]] = defaultdict(list)
    for evidence in mapped:
        grouped[evidence.skill_id].append(evidence)

    skills: list[AggregatedUserSkill] = []
    for skill_id, raw_evidences in grouped.items():
        evidences = _deduplicate_evidences(raw_evidences)
        first = evidences[0]
        level, level_confidence = _level_and_confidence(first.category, evidences)
        skills.append(
            AggregatedUserSkill(
                skill_id=skill_id,
                skill_name=first.skill_name,
                category=first.category,
                level=level,
                mapping_confidence=max(
                    evidence.mapping_confidence for evidence in evidences
                ),
                level_confidence=level_confidence,
                evidences=evidences,
            )
        )
    return sorted(skills, key=lambda item: (item.category.value, item.skill_name))


def build_user_skill_mapping_report(
    conn: Any,
    extraction: SkillExtractionReport,
    *,
    embedding_client: Any | None = None,
) -> UserSkillMappingReport:
    mapped, unmapped = map_extracted_candidates(
        conn, extraction, embedding_client=embedding_client
    )
    return UserSkillMappingReport(
        user_id=extraction.user_id,
        extraction_model=extraction.model,
        processed_chunk_count=len(extraction.chunks),
        processed_chunks=[
            ProcessedChunkRef(
                source_kind=chunk.source_kind,
                chunk_id=chunk.chunk_id,
                content_hash=chunk.content_hash,
            )
            for chunk in extraction.chunks
        ],
        skills=aggregate_mapped_evidences(mapped),
        unmapped=unmapped,
        extraction_failures=extraction.failures,
    )


def _validate_processed_chunks(conn: Any, report: UserSkillMappingReport) -> None:
    """저장 직전 청크 소유권과 content_hash가 추출 시점 그대로인지 확인한다."""
    expected_by_source: dict[str, dict[int, str]] = {
        "RESUME": {},
        "COVER_LETTER": {},
    }
    for chunk in report.processed_chunks:
        if chunk.source_kind not in expected_by_source:
            raise ValueError(f"지원하지 않는 근거 출처입니다: {chunk.source_kind}")
        expected_by_source[chunk.source_kind][chunk.chunk_id] = chunk.content_hash

    actual_by_source: dict[str, dict[int, str]] = {
        "RESUME": {},
        "COVER_LETTER": {},
    }
    with conn.cursor() as cur:
        resume_ids = list(expected_by_source["RESUME"])
        if resume_ids:
            cur.execute(
                "SELECT rc.id, rc.content_hash FROM resume_chunks rc "
                "JOIN resumes r ON r.id = rc.resume_id "
                "WHERE r.user_id = %s AND rc.id = ANY(%s)",
                (report.user_id, resume_ids),
            )
            actual_by_source["RESUME"] = {
                int(row["id"] if isinstance(row, dict) else row[0]):
                str(row["content_hash"] if isinstance(row, dict) else row[1])
                for row in cur.fetchall()
            }

        cover_ids = list(expected_by_source["COVER_LETTER"])
        if cover_ids:
            cur.execute(
                "SELECT cc.id, cc.content_hash FROM cover_letter_chunks cc "
                "JOIN cover_letter_items ci ON ci.id = cc.cover_letter_item_id "
                "JOIN cover_letters cl ON cl.id = ci.cover_letter_id "
                "WHERE cl.user_id = %s AND cc.id = ANY(%s)",
                (report.user_id, cover_ids),
            )
            actual_by_source["COVER_LETTER"] = {
                int(row["id"] if isinstance(row, dict) else row[0]):
                str(row["content_hash"] if isinstance(row, dict) else row[1])
                for row in cur.fetchall()
            }

    if actual_by_source != expected_by_source:
        raise ValueError(
            "추출 후 청크가 변경·삭제되었거나 다른 사용자의 청크입니다. "
            "청킹·임베딩·추출부터 다시 실행하세요."
        )


def persist_user_skill_mapping(
    conn: Any,
    report: UserSkillMappingReport,
) -> PersistStats:
    """사용자 한 명의 AI 소유 스킬·근거를 완전 동기화한다."""
    if report.extraction_failures:
        raise ValueError("추출 실패 청크가 있어 기존 근거를 안전하게 동기화할 수 없습니다.")
    if report.processed_chunk_count == 0:
        raise ValueError("처리된 청크가 0건이므로 기존 스킬을 삭제하지 않습니다.")
    if report.processed_chunk_count != len(report.processed_chunks):
        raise ValueError("처리 청크 개수와 content_hash 참조 개수가 다릅니다.")
    _validate_processed_chunks(conn, report)

    with conn.cursor() as cur:
        # Q. 같은 사용자를 동시에 처리하면 어떻게 하나요?
        # A. 트랜잭션 범위 advisory lock으로 두 배치가 근거를 서로 지우는 것을 막습니다.
        cur.execute(
            "SELECT pg_advisory_xact_lock(hashtext('resume_skill_mapping'), %s)",
            (report.user_id,),
        )
        cur.execute(
            "DELETE FROM user_skill_evidences usev USING user_skills us "
            "WHERE usev.user_skill_id = us.id AND us.user_id = %s",
            (report.user_id,),
        )
        evidence_deleted = cur.rowcount

        skill_ids: dict[int, int] = {}
        for skill in report.skills:
            cur.execute(
                "INSERT INTO user_skills "
                "(user_id, skill_id, skill_level, mapping_confidence, level_confidence) "
                "VALUES (%s, %s, %s, %s, %s) "
                "ON CONFLICT (user_id, skill_id) DO UPDATE SET "
                "skill_level = EXCLUDED.skill_level, "
                "mapping_confidence = EXCLUDED.mapping_confidence, "
                "level_confidence = EXCLUDED.level_confidence, "
                "updated_at = CURRENT_TIMESTAMP "
                "RETURNING id",
                (
                    report.user_id,
                    skill.skill_id,
                    skill.level,
                    skill.mapping_confidence,
                    skill.level_confidence,
                ),
            )
            row = cur.fetchone()
            skill_ids[skill.skill_id] = int(row["id"] if isinstance(row, dict) else row[0])

        evidence_inserted = 0
        for skill in report.skills:
            user_skill_id = skill_ids[skill.skill_id]
            for evidence in skill.evidences:
                resume_chunk_id = (
                    evidence.chunk_id if evidence.source_kind == "RESUME" else None
                )
                cover_letter_chunk_id = (
                    evidence.chunk_id
                    if evidence.source_kind == "COVER_LETTER"
                    else None
                )
                cur.execute(
                    "INSERT INTO user_skill_evidences "
                    "(user_skill_id, resume_chunk_id, cover_letter_chunk_id, "
                    "extracted_name, evidence_text, extracted_level, mapping_method, "
                    "mapping_similarity, mapping_confidence) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
                    (
                        user_skill_id,
                        resume_chunk_id,
                        cover_letter_chunk_id,
                        evidence.extracted_name,
                        evidence.evidence,
                        evidence.extracted_level,
                        evidence.mapping_method.value,
                        evidence.mapping_similarity,
                        evidence.mapping_confidence,
                    ),
                )
                evidence_inserted += 1

        # Q. 이전 실행에는 있었지만 현재 근거가 0개인 스킬은 어떻게 하나요?
        # A. 사용자가 합의한 파생 데이터 정책에 따라 삭제합니다. 유지되는 행의
        #    is_important는 UPSERT에서 건드리지 않으므로 사용자 선택이 보존됩니다.
        cur.execute(
            "DELETE FROM user_skills us WHERE us.user_id = %s "
            "AND NOT EXISTS ("
            "SELECT 1 FROM user_skill_evidences usev WHERE usev.user_skill_id = us.id"
            ")",
            (report.user_id,),
        )
        skill_deleted = cur.rowcount

    return PersistStats(
        evidence_deleted=evidence_deleted,
        skill_upserted=len(report.skills),
        evidence_inserted=evidence_inserted,
        skill_deleted=skill_deleted,
    )

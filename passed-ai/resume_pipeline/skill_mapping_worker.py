"""skills 마스터에 대한 저장 없는 EXACT·정규화·임베딩 매핑 분석."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import re
import unicodedata
from typing import Any

from pydantic import BaseModel, Field

from .embedding_worker import _create_embeddings, _create_openai_client
from .skill_extraction_models import SkillCategory
from .skill_mapping_models import (
    MappingExpectation,
    MappingMethod,
    SkillMappingGoldenCase,
)


class MappingFailureReason(str, Enum):
    CATEGORY_MISMATCH = "CATEGORY_MISMATCH"
    LOW_SIMILARITY = "LOW_SIMILARITY"
    AMBIGUOUS_MATCH = "AMBIGUOUS_MATCH"


@dataclass(frozen=True)
class SkillMaster:
    skill_id: int
    name: str
    category: SkillCategory
    has_embedding: bool


@dataclass(frozen=True)
class SkillAlias:
    alias_id: int
    skill_id: int
    skill_name: str
    category: SkillCategory
    alias: str


class SimilarityHit(BaseModel):
    skill_id: int
    name: str
    category: SkillCategory
    similarity: float


class RawMappingResult(BaseModel):
    case_id: str
    expectation: MappingExpectation
    extracted_name: str
    extracted_category: SkillCategory
    resolved_method: MappingMethod | None = None
    resolved_skill_name: str | None = None
    normalized_candidates: list[str] = Field(default_factory=list)
    global_top_k: list[SimilarityHit] = Field(default_factory=list)
    category_top_k: list[SimilarityHit] = Field(default_factory=list)
    expected_similarity: float | None = None
    expected_global_rank: int | None = None
    expected_category_rank: int | None = None

    @property
    def category_top1_margin(self) -> float | None:
        if len(self.category_top_k) < 2:
            return None
        return self.category_top_k[0].similarity - self.category_top_k[1].similarity


class RawMappingReport(BaseModel):
    model: str
    top_k: int
    total_cases: int
    exact_resolved: int
    normalized_resolved: int
    alias_resolved: int = 0
    embedding_analyzed: int
    category_mismatch_cases: int
    missing_master_embeddings: int
    results: list[RawMappingResult]


class MappingDecision(BaseModel):
    case_id: str
    mapped: bool
    method: MappingMethod | None = None
    skill_name: str | None = None
    similarity: float | None = None
    margin: float | None = None
    failure_reason: MappingFailureReason | None = None


_IGNORABLE_SEPARATORS = re.compile(r"[\s._\-]+")
_CERTIFICATION_SCORE_SUFFIX = re.compile(r"\s+\d+(?:\.\d+)?\s*(?:점|급|등급)$")


def normalize_skill_name(value: str) -> str:
    """의미를 바꾸지 않는 표기 차이만 제거한다."""
    normalized = unicodedata.normalize("NFKC", value).casefold().strip()
    return _IGNORABLE_SEPARATORS.sub("", normalized)


def normalize_alias_candidate(value: str, category: SkillCategory) -> str:
    """카테고리상 확실한 구조 정보만 제거한 뒤 별칭 비교값을 만든다."""
    candidate = value.strip()
    if category is SkillCategory.CERTIFICATION:
        candidate = _CERTIFICATION_SCORE_SUFFIX.sub("", candidate)
    return normalize_skill_name(candidate)


def apply_mapping_thresholds(
    result: RawMappingResult,
    *,
    min_similarity: float,
    min_margin: float,
) -> MappingDecision:
    """raw 결과에 임계값을 적용하되 실패 이유를 세 종류로 제한한다."""
    if result.resolved_method and result.resolved_skill_name:
        return MappingDecision(
            case_id=result.case_id,
            mapped=True,
            method=result.resolved_method,
            skill_name=result.resolved_skill_name,
        )

    same_category = result.category_top_k
    global_hits = result.global_top_k
    if not same_category:
        return MappingDecision(
            case_id=result.case_id,
            mapped=False,
            failure_reason=MappingFailureReason.LOW_SIMILARITY,
        )

    top1 = same_category[0]
    margin = result.category_top1_margin
    global_top1 = global_hits[0] if global_hits else None

    if (
        top1.similarity < min_similarity
        and global_top1 is not None
        and global_top1.category is not result.extracted_category
        and global_top1.similarity >= min_similarity
    ):
        return MappingDecision(
            case_id=result.case_id,
            mapped=False,
            similarity=top1.similarity,
            margin=margin,
            failure_reason=MappingFailureReason.CATEGORY_MISMATCH,
        )
    if top1.similarity < min_similarity:
        return MappingDecision(
            case_id=result.case_id,
            mapped=False,
            similarity=top1.similarity,
            margin=margin,
            failure_reason=MappingFailureReason.LOW_SIMILARITY,
        )
    if margin is not None and margin < min_margin:
        return MappingDecision(
            case_id=result.case_id,
            mapped=False,
            similarity=top1.similarity,
            margin=margin,
            failure_reason=MappingFailureReason.AMBIGUOUS_MATCH,
        )
    return MappingDecision(
        case_id=result.case_id,
        mapped=True,
        method=MappingMethod.EMBEDDING,
        skill_name=top1.name,
        similarity=top1.similarity,
        margin=margin,
    )


def load_skill_masters(conn: Any) -> list[SkillMaster]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, category, embedding IS NOT NULL AS has_embedding "
            "FROM skills WHERE category IS NOT NULL ORDER BY id"
        )
        rows = cur.fetchall()
    return [
        SkillMaster(
            skill_id=int(row["id"] if isinstance(row, dict) else row[0]),
            name=str(row["name"] if isinstance(row, dict) else row[1]),
            category=SkillCategory(
                row["category"] if isinstance(row, dict) else row[2]
            ),
            has_embedding=bool(
                row["has_embedding"] if isinstance(row, dict) else row[3]
            ),
        )
        for row in rows
    ]


def load_skill_aliases(conn: Any) -> list[SkillAlias]:
    """활성 별칭과 연결된 마스터를 읽는다.
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT sa.id, sa.skill_id, s.name, s.category, sa.alias "
            "FROM skill_aliases sa JOIN skills s ON s.id = sa.skill_id "
            "WHERE sa.is_active = TRUE ORDER BY sa.id"
        )
        rows = cur.fetchall()
    return [
        SkillAlias(
            alias_id=int(row["id"] if isinstance(row, dict) else row[0]),
            skill_id=int(row["skill_id"] if isinstance(row, dict) else row[1]),
            skill_name=str(row["name"] if isinstance(row, dict) else row[2]),
            category=SkillCategory(
                row["category"] if isinstance(row, dict) else row[3]
            ),
            alias=str(row["alias"] if isinstance(row, dict) else row[4]),
        )
        for row in rows
    ]


def resolve_alias(
    case: SkillMappingGoldenCase,
    aliases: list[SkillAlias],
) -> tuple[SkillAlias | None, list[SkillAlias]]:
    """같은 카테고리의 정확·정규화 별칭이 하나일 때만 안전하게 선택한다."""
    same_category = [
        alias for alias in aliases if alias.category is case.extracted_category
    ]
    exact = [alias for alias in same_category if alias.alias == case.extracted_name]
    if len(exact) == 1:
        return exact[0], exact

    normalized_name = normalize_alias_candidate(
        case.extracted_name, case.extracted_category
    )
    normalized = [
        alias
        for alias in same_category
        if normalize_alias_candidate(alias.alias, alias.category) == normalized_name
    ]
    if len(normalized) == 1:
        return normalized[0], normalized

    return None, normalized or exact


def resolve_exact_or_normalized(
    case: SkillMappingGoldenCase,
    masters: list[SkillMaster],
) -> tuple[MappingMethod | None, SkillMaster | None, list[SkillMaster]]:
    same_category = [
        skill for skill in masters if skill.category is case.extracted_category
    ]
    exact = [skill for skill in same_category if skill.name == case.extracted_name]
    if len(exact) == 1:
        return MappingMethod.EXACT, exact[0], exact

    normalized_name = normalize_skill_name(case.extracted_name)
    normalized = [
        skill
        for skill in same_category
        if normalize_skill_name(skill.name) == normalized_name
    ]
    if len(normalized) == 1:
        return MappingMethod.NORMALIZED, normalized[0], normalized
    return None, None, normalized


def _similarity_rows(
    conn: Any,
    vector: list[float],
    *,
    category: SkillCategory | None,
    limit: int,
) -> list[SimilarityHit]:
    category_sql = "AND category = %s" if category is not None else ""
    params: tuple[Any, ...] = (
        (vector, category.value, vector, limit)
        if category is not None
        else (vector, vector, limit)
    )
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, category, "
            "1 - (embedding <=> %s::vector) AS similarity "
            "FROM skills WHERE embedding IS NOT NULL "
            f"{category_sql} ORDER BY embedding <=> %s::vector LIMIT %s",
            params,
        )
        rows = cur.fetchall()
    return [
        SimilarityHit(
            skill_id=int(row["id"] if isinstance(row, dict) else row[0]),
            name=str(row["name"] if isinstance(row, dict) else row[1]),
            category=SkillCategory(
                row["category"] if isinstance(row, dict) else row[2]
            ),
            similarity=float(
                row["similarity"] if isinstance(row, dict) else row[3]
            ),
        )
        for row in rows
    ]


def _expected_similarity_and_ranks(
    conn: Any,
    vector: list[float],
    case: SkillMappingGoldenCase,
) -> tuple[float | None, int | None, int | None]:
    if not case.should_map:
        return None, None, None
    with conn.cursor() as cur:
        cur.execute(
            "SELECT 1 - (embedding <=> %s::vector) AS similarity, "
            "1 + (SELECT COUNT(*) FROM skills s2 WHERE s2.embedding IS NOT NULL "
            "AND (s2.embedding <=> %s::vector) < (s.embedding <=> %s::vector)) "
            "AS global_rank, "
            "1 + (SELECT COUNT(*) FROM skills s3 WHERE s3.embedding IS NOT NULL "
            "AND s3.category = s.category "
            "AND (s3.embedding <=> %s::vector) < (s.embedding <=> %s::vector)) "
            "AS category_rank FROM skills s "
            "WHERE s.name = %s AND s.category = %s AND s.embedding IS NOT NULL",
            (
                vector,
                vector,
                vector,
                vector,
                vector,
                case.expected_skill_name,
                case.expected_skill_category.value,
            ),
        )
        row = cur.fetchone()
    if row is None:
        return None, None, None
    return (
        float(row["similarity"] if isinstance(row, dict) else row[0]),
        int(row["global_rank"] if isinstance(row, dict) else row[1]),
        int(row["category_rank"] if isinstance(row, dict) else row[2]),
    )


def analyze_mapping_golden_set(
    conn: Any,
    cases: list[SkillMappingGoldenCase],
    *,
    top_k: int = 3,
    embedding_client: Any | None = None,
) -> RawMappingReport:
    """임계값을 적용하지 않고 단계별 해석과 raw 유사도 분포를 만든다."""
    if top_k < 2:
        raise ValueError("top_k는 마진 계산을 위해 2 이상이어야 합니다.")
    masters = load_skill_masters(conn)
    aliases = load_skill_aliases(conn)
    missing_embeddings = sum(not skill.has_embedding for skill in masters)
    results: list[RawMappingResult] = []
    embedding_cases: list[SkillMappingGoldenCase] = []

    for case in cases:
        method, resolved, normalized_matches = resolve_exact_or_normalized(
            case, masters
        )
        if method and resolved:
            results.append(
                RawMappingResult(
                    case_id=case.case_id,
                    expectation=case.expectation,
                    extracted_name=case.extracted_name,
                    extracted_category=case.extracted_category,
                    resolved_method=method,
                    resolved_skill_name=resolved.name,
                    normalized_candidates=[item.name for item in normalized_matches],
                )
            )
        else:
            alias, alias_matches = resolve_alias(case, aliases)
            if alias:
                results.append(
                    RawMappingResult(
                        case_id=case.case_id,
                        expectation=case.expectation,
                        extracted_name=case.extracted_name,
                        extracted_category=case.extracted_category,
                        resolved_method=MappingMethod.ALIAS,
                        resolved_skill_name=alias.skill_name,
                        normalized_candidates=[
                            item.alias for item in alias_matches
                        ],
                    )
                )
            else:
                embedding_cases.append(case)

    if embedding_cases:
        client = embedding_client or _create_openai_client()
        vectors = _create_embeddings(
            client, [case.extracted_name for case in embedding_cases]
        )
        for case, vector in zip(embedding_cases, vectors, strict=True):
            global_hits = _similarity_rows(
                conn, vector, category=None, limit=top_k
            )
            category_hits = _similarity_rows(
                conn, vector, category=case.extracted_category, limit=top_k
            )
            expected_similarity, global_rank, category_rank = (
                _expected_similarity_and_ranks(conn, vector, case)
            )
            results.append(
                RawMappingResult(
                    case_id=case.case_id,
                    expectation=case.expectation,
                    extracted_name=case.extracted_name,
                    extracted_category=case.extracted_category,
                    normalized_candidates=[],
                    global_top_k=global_hits,
                    category_top_k=category_hits,
                    expected_similarity=expected_similarity,
                    expected_global_rank=global_rank,
                    expected_category_rank=category_rank,
                )
            )

    order = {case.case_id: index for index, case in enumerate(cases)}
    results.sort(key=lambda item: order[item.case_id])
    category_mismatch_cases = sum(
        bool(result.global_top_k)
        and result.global_top_k[0].category is not result.extracted_category
        for result in results
    )
    from .embedding_worker import EMBEDDING_MODEL

    return RawMappingReport(
        model=EMBEDDING_MODEL,
        top_k=top_k,
        total_cases=len(cases),
        exact_resolved=sum(
            item.resolved_method is MappingMethod.EXACT for item in results
        ),
        normalized_resolved=sum(
            item.resolved_method is MappingMethod.NORMALIZED for item in results
        ),
        alias_resolved=sum(
            item.resolved_method is MappingMethod.ALIAS for item in results
        ),
        embedding_analyzed=len(embedding_cases),
        category_mismatch_cases=category_mismatch_cases,
        missing_master_embeddings=missing_embeddings,
        results=results,
    )

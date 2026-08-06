"""마스터 설명을 제외하고 스킬 이름만 임베딩하는 읽기 전용 비교 실험."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Callable
from enum import Enum
from statistics import mean, median

import numpy as np
from pydantic import BaseModel, Field

from .embedding_worker import EMBEDDING_BATCH_SIZE, EMBEDDING_MODEL
from .skill_extraction_models import SkillCategory
from .skill_mapping_models import MappingExpectation, MappingMethod, SkillMappingGoldenCase
from .skill_mapping_worker import (
    RawMappingResult,
    SimilarityHit,
    SkillMaster,
    resolve_exact_or_normalized,
)


class NameOnlyStrategy(str, Enum):
    NAME_ONLY = "NAME_ONLY"
    NAME_ONLY_WITH_ALIASES = "NAME_ONLY_WITH_ALIASES"
    ALIAS_PRIMARY = "ALIAS_PRIMARY"


class NumericDistribution(BaseModel):
    count: int
    minimum: float | None = None
    median: float | None = None
    mean: float | None = None
    maximum: float | None = None
    p25: float | None = None
    p75: float | None = None


class CategoryRetrievalSummary(BaseModel):
    cases: int
    top1: int
    top1_rate: float
    top3: int
    top3_rate: float
    expected_similarity: NumericDistribution
    top1_margin: NumericDistribution


class NameOnlyExperimentSummary(BaseModel):
    total_cases: int
    exact_resolved: int
    normalized_resolved: int
    embedding_analyzed: int
    map_embedding_cases: int
    expected_category_top1: int
    expected_category_top1_rate: float
    expected_category_top3: int
    expected_category_top3_rate: float
    category_mismatch_cases: int
    missing_expected_master_cases: list[str] = Field(default_factory=list)
    expected_similarity: NumericDistribution
    top1_margin: NumericDistribution
    no_match_top1_similarity: NumericDistribution
    by_category: dict[str, CategoryRetrievalSummary]
    recommended_strategy: NameOnlyStrategy
    decision_reason: str


class NameOnlyExperimentReport(BaseModel):
    model: str
    master_embedding_text: str = "name_only"
    top_k: int
    summary: NameOnlyExperimentSummary
    results: list[RawMappingResult]


Embedder = Callable[[list[str]], list[list[float]]]


def embed_in_batches(
    texts: list[str],
    embedder: Embedder,
    *,
    batch_size: int = EMBEDDING_BATCH_SIZE,
) -> list[list[float]]:
    """API 제한에 맞춰 임베딩하되 입력 순서를 그대로 보존한다."""
    if batch_size < 1:
        raise ValueError("batch_size는 1 이상이어야 합니다.")
    vectors: list[list[float]] = []
    for start in range(0, len(texts), batch_size):
        vectors.extend(embedder(texts[start : start + batch_size]))
    if len(vectors) != len(texts):
        raise ValueError(
            f"임베딩 개수 불일치: expected={len(texts)} actual={len(vectors)}"
        )
    return vectors


def _unit_matrix(vectors: list[list[float]]) -> np.ndarray:
    matrix = np.asarray(vectors, dtype=np.float32)
    if matrix.ndim != 2 or not len(matrix):
        raise ValueError("비어 있지 않은 2차원 임베딩 배열이 필요합니다.")
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    if np.any(norms == 0):
        raise ValueError("크기가 0인 임베딩은 비교할 수 없습니다.")
    return matrix / norms


def _distribution(values: list[float]) -> NumericDistribution:
    if not values:
        return NumericDistribution(count=0)
    array = np.asarray(values, dtype=np.float64)
    return NumericDistribution(
        count=len(values),
        minimum=float(min(values)),
        median=float(median(values)),
        mean=float(mean(values)),
        maximum=float(max(values)),
        p25=float(np.percentile(array, 25)),
        p75=float(np.percentile(array, 75)),
    )


def _hits(
    scores: np.ndarray,
    indexes: list[int],
    masters: list[SkillMaster],
    top_k: int,
) -> list[SimilarityHit]:
    ranked = sorted(indexes, key=lambda index: float(scores[index]), reverse=True)
    return [
        SimilarityHit(
            skill_id=masters[index].skill_id,
            name=masters[index].name,
            category=masters[index].category,
            similarity=float(scores[index]),
        )
        for index in ranked[:top_k]
    ]


def _rank(scores: np.ndarray, indexes: list[int], target_index: int) -> int:
    target_score = float(scores[target_index])
    return 1 + sum(float(scores[index]) > target_score for index in indexes)


def _strategy(top1_rate: float) -> tuple[NameOnlyStrategy, str]:
    # Q. 왜 유사도 평균이 아니라 top-1 비율로 전략을 고르나요?
    # A. 자동 매핑은 결국 한 스킬을 선택해야 합니다. 정답 점수가 올라도 다른 스킬이
    #    계속 1위라면 임계값으로 해결할 수 없으므로 검색 순위를 기준으로 판단합니다.
    if top1_rate >= 0.60:
        return (
            NameOnlyStrategy.NAME_ONLY,
            "top-1이 60% 이상이므로 별칭 없이 이름 임베딩으로 임계값 평가를 진행합니다.",
        )
    if top1_rate >= 0.40:
        return (
            NameOnlyStrategy.NAME_ONLY_WITH_ALIASES,
            "top-1이 40% 이상 60% 미만이므로 이름 임베딩과 별칭 테이블을 병행합니다.",
        )
    return (
        NameOnlyStrategy.ALIAS_PRIMARY,
        "top-1이 40% 미만이므로 임베딩은 보조로 두고 별칭 매핑을 우선합니다.",
    )


def analyze_name_only_embeddings(
    masters: list[SkillMaster],
    cases: list[SkillMappingGoldenCase],
    embedder: Embedder,
    *,
    top_k: int = 3,
    batch_size: int = EMBEDDING_BATCH_SIZE,
) -> NameOnlyExperimentReport:
    """DB를 수정하지 않고 후보 이름과 마스터 이름의 검색 품질을 측정한다."""
    if top_k < 2:
        raise ValueError("top_k는 마진 계산을 위해 2 이상이어야 합니다.")
    if not masters:
        raise ValueError("skills 마스터가 비어 있습니다.")

    results: list[RawMappingResult] = []
    embedding_cases: list[SkillMappingGoldenCase] = []
    for case in cases:
        method, resolved, normalized_matches = resolve_exact_or_normalized(case, masters)
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
            embedding_cases.append(case)

    # Q. 왜 기존 skills.embedding을 덮어쓰지 않나요?
    # A. 지금은 이름-only 방식이 더 좋은지 검증하는 실험입니다. 검증 전 운영 데이터를
    #    바꾸면 기존 공고 파이프라인 결과까지 영향을 주므로 메모리에서만 비교합니다.
    master_vectors = embed_in_batches(
        [master.name for master in masters], embedder, batch_size=batch_size
    )
    candidate_vectors = embed_in_batches(
        [case.extracted_name for case in embedding_cases],
        embedder,
        batch_size=batch_size,
    )
    master_matrix = _unit_matrix(master_vectors)
    candidate_matrix = _unit_matrix(candidate_vectors)
    similarities = candidate_matrix @ master_matrix.T

    all_indexes = list(range(len(masters)))
    indexes_by_category: dict[SkillCategory, list[int]] = defaultdict(list)
    target_indexes: dict[tuple[str, SkillCategory], int] = {}
    for index, master in enumerate(masters):
        indexes_by_category[master.category].append(index)
        target_indexes.setdefault((master.name, master.category), index)

    missing_expected: list[str] = []
    for row_index, case in enumerate(embedding_cases):
        scores = similarities[row_index]
        category_indexes = indexes_by_category[case.extracted_category]
        global_hits = _hits(scores, all_indexes, masters, top_k)
        category_hits = _hits(scores, category_indexes, masters, top_k)

        expected_similarity = global_rank = category_rank = None
        if case.expectation is MappingExpectation.MAP:
            target_key = (case.expected_skill_name, case.expected_skill_category)
            target_index = target_indexes.get(target_key)
            if target_index is None:
                missing_expected.append(case.case_id)
            else:
                expected_similarity = float(scores[target_index])
                global_rank = _rank(scores, all_indexes, target_index)
                category_rank = _rank(scores, category_indexes, target_index)

        results.append(
            RawMappingResult(
                case_id=case.case_id,
                expectation=case.expectation,
                extracted_name=case.extracted_name,
                extracted_category=case.extracted_category,
                global_top_k=global_hits,
                category_top_k=category_hits,
                expected_similarity=expected_similarity,
                expected_global_rank=global_rank,
                expected_category_rank=category_rank,
            )
        )

    order = {case.case_id: index for index, case in enumerate(cases)}
    results.sort(key=lambda item: order[item.case_id])
    map_results = [
        item
        for item in results
        if item.expectation is MappingExpectation.MAP and item.resolved_method is None
    ]
    top1_count = sum(item.expected_category_rank == 1 for item in map_results)
    top3_count = sum(
        item.expected_category_rank is not None and item.expected_category_rank <= 3
        for item in map_results
    )
    denominator = len(map_results)
    top1_rate = top1_count / denominator if denominator else 0.0
    strategy, reason = _strategy(top1_rate)

    by_category: dict[str, CategoryRetrievalSummary] = {}
    for category in SkillCategory:
        items = [item for item in map_results if item.extracted_category is category]
        if not items:
            continue
        category_top1 = sum(item.expected_category_rank == 1 for item in items)
        category_top3 = sum(
            item.expected_category_rank is not None and item.expected_category_rank <= 3
            for item in items
        )
        by_category[category.value] = CategoryRetrievalSummary(
            cases=len(items),
            top1=category_top1,
            top1_rate=category_top1 / len(items),
            top3=category_top3,
            top3_rate=category_top3 / len(items),
            expected_similarity=_distribution(
                [item.expected_similarity for item in items if item.expected_similarity is not None]
            ),
            top1_margin=_distribution(
                [item.category_top1_margin for item in items if item.category_top1_margin is not None]
            ),
        )

    no_match_results = [
        item for item in results if item.expectation is MappingExpectation.NO_MATCH
    ]
    summary = NameOnlyExperimentSummary(
        total_cases=len(cases),
        exact_resolved=sum(item.resolved_method is MappingMethod.EXACT for item in results),
        normalized_resolved=sum(
            item.resolved_method is MappingMethod.NORMALIZED for item in results
        ),
        embedding_analyzed=len(embedding_cases),
        map_embedding_cases=denominator,
        expected_category_top1=top1_count,
        expected_category_top1_rate=top1_rate,
        expected_category_top3=top3_count,
        expected_category_top3_rate=top3_count / denominator if denominator else 0.0,
        category_mismatch_cases=sum(
            bool(item.global_top_k)
            and item.global_top_k[0].category is not item.extracted_category
            for item in results
        ),
        missing_expected_master_cases=missing_expected,
        expected_similarity=_distribution(
            [
                item.expected_similarity
                for item in map_results
                if item.expected_similarity is not None
            ]
        ),
        top1_margin=_distribution(
            [
                item.category_top1_margin
                for item in map_results
                if item.category_top1_margin is not None
            ]
        ),
        no_match_top1_similarity=_distribution(
            [
                item.category_top_k[0].similarity
                for item in no_match_results
                if item.category_top_k
            ]
        ),
        by_category=by_category,
        recommended_strategy=strategy,
        decision_reason=reason,
    )
    return NameOnlyExperimentReport(
        model=EMBEDDING_MODEL,
        top_k=top_k,
        summary=summary,
        results=results,
    )

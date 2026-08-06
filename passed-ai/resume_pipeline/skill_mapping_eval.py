"""임계값이 적용된 매핑 결정을 골든셋과 비교하는 평가기."""

from __future__ import annotations

from collections import Counter

from pydantic import BaseModel

from .skill_mapping_models import MappingExpectation, MappingMethod, SkillMappingGoldenCase
from .skill_mapping_worker import (
    MappingDecision,
    MappingFailureReason,
    RawMappingReport,
    apply_mapping_thresholds,
)


class MethodMetrics(BaseModel):
    mapped: int
    correct: int
    accuracy: float


class MappingEvaluationReport(BaseModel):
    min_similarity: float
    min_margin: float
    total_cases: int
    map_cases: int
    non_map_cases: int
    automatically_mapped: int
    correctly_mapped: int
    wrongly_mapped: int
    mapping_accuracy: float
    mapping_coverage: float
    correctly_unmapped: int
    unmapped_accuracy: float
    category_mismatch_rate: float
    failures_by_reason: dict[str, int]
    by_method: dict[str, MethodMetrics]
    decisions: list[MappingDecision]


def evaluate_mapping_report(
    raw: RawMappingReport,
    cases: list[SkillMappingGoldenCase],
    *,
    min_similarity: float,
    min_margin: float,
) -> MappingEvaluationReport:
    """EXACT·NORMALIZED·ALIAS와 보조 임베딩을 같은 계약으로 평가한다."""
    case_by_id = {case.case_id: case for case in cases}
    decisions = [
        apply_mapping_thresholds(
            result,
            min_similarity=min_similarity,
            min_margin=min_margin,
        )
        for result in raw.results
    ]

    method_totals: Counter[MappingMethod] = Counter()
    method_correct: Counter[MappingMethod] = Counter()
    failures: Counter[MappingFailureReason] = Counter()
    correctly_mapped = wrongly_mapped = correctly_unmapped = 0

    for decision in decisions:
        case = case_by_id[decision.case_id]
        if decision.mapped:
            assert decision.method is not None
            method_totals[decision.method] += 1
            is_correct = (
                case.expectation is MappingExpectation.MAP
                and decision.skill_name == case.expected_skill_name
            )
            if is_correct:
                correctly_mapped += 1
                method_correct[decision.method] += 1
            else:
                wrongly_mapped += 1
        else:
            if case.expectation is not MappingExpectation.MAP:
                correctly_unmapped += 1
            if decision.failure_reason is not None:
                failures[decision.failure_reason] += 1

    map_cases = sum(case.expectation is MappingExpectation.MAP for case in cases)
    non_map_cases = len(cases) - map_cases
    automatically_mapped = correctly_mapped + wrongly_mapped
    by_method = {
        method.value: MethodMetrics(
            mapped=method_totals[method],
            correct=method_correct[method],
            accuracy=(
                method_correct[method] / method_totals[method]
                if method_totals[method]
                else 0.0
            ),
        )
        for method in MappingMethod
        if method_totals[method]
    }

    # Q. 커버리지 분모를 전체 53건으로 두지 않는 이유는 무엇인가요?
    # A. NO_MATCH는 연결하지 않는 것이 정답입니다. 실제로 연결해야 하는 MAP 47건 중
    #    몇 건을 올바르게 자동 처리했는지가 자동 매핑 커버리지입니다.
    return MappingEvaluationReport(
        min_similarity=min_similarity,
        min_margin=min_margin,
        total_cases=len(cases),
        map_cases=map_cases,
        non_map_cases=non_map_cases,
        automatically_mapped=automatically_mapped,
        correctly_mapped=correctly_mapped,
        wrongly_mapped=wrongly_mapped,
        mapping_accuracy=(
            correctly_mapped / automatically_mapped if automatically_mapped else 0.0
        ),
        mapping_coverage=correctly_mapped / map_cases if map_cases else 0.0,
        correctly_unmapped=correctly_unmapped,
        unmapped_accuracy=(
            correctly_unmapped / non_map_cases if non_map_cases else 0.0
        ),
        category_mismatch_rate=(
            failures[MappingFailureReason.CATEGORY_MISMATCH] / len(cases)
            if cases
            else 0.0
        ),
        failures_by_reason={reason.value: failures[reason] for reason in MappingFailureReason},
        by_method=by_method,
        decisions=decisions,
    )

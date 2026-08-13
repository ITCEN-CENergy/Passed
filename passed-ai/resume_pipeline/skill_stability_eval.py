"""같은 입력을 여러 번 추출했을 때 후보 집합의 Jaccard 안정성을 계산한다."""

from __future__ import annotations

from itertools import combinations

from pydantic import BaseModel, Field

from .skill_extraction_eval import PredictedExample
from .skill_extraction_eval import ExpectedSkill
from .skill_extraction_models import SkillExtractionReport


class ExampleStability(BaseModel):
    example_id: str
    pair_count: int = Field(ge=1)
    mean_jaccard: float = Field(ge=0, le=1)
    min_jaccard: float = Field(ge=0, le=1)


class StabilityReport(BaseModel):
    run_count: int = Field(ge=2)
    example_count: int = Field(ge=0)
    pair_count: int = Field(ge=1)
    macro_mean_jaccard: float = Field(ge=0, le=1)
    macro_min_jaccard: float = Field(ge=0, le=1)
    corpus_mean_jaccard: float = Field(ge=0, le=1)
    corpus_min_jaccard: float = Field(ge=0, le=1)
    examples: list[ExampleStability]


def _candidate_tokens(example: PredictedExample) -> set[tuple[str, str]]:
    return {
        (
            " ".join(skill.extracted_name.casefold().split()),
            skill.category.value,
        )
        for skill in example.predicted
    }


def _jaccard(left: set[object], right: set[object]) -> float:
    # Q. 두 실행이 모두 빈 배열이면 왜 1.0인가요?
    # A. 같은 입력에 대해 둘 다 아무 후보도 만들지 않았으므로 결과 집합이 완전히
    #    동일합니다. 음성 예제의 안정성을 0으로 왜곡하지 않기 위해 1.0으로 봅니다.
    union = left | right
    if not union:
        return 1.0
    return len(left & right) / len(union)


def evaluate_prediction_stability(
    runs: list[list[PredictedExample]],
) -> StabilityReport:
    if len(runs) < 2:
        raise ValueError("안정성 평가는 최소 2회 실행 결과가 필요합니다.")

    indexed_runs = [{item.example_id: item for item in run} for run in runs]
    expected_ids = set(indexed_runs[0])
    for index, run in enumerate(indexed_runs, start=1):
        if len(run) != len(runs[index - 1]):
            raise ValueError(f"{index}번째 실행에 중복 example_id가 있습니다.")
        if set(run) != expected_ids:
            raise ValueError("모든 실행의 example_id 집합이 같아야 합니다.")

    pair_indexes = list(combinations(range(len(runs)), 2))
    examples: list[ExampleStability] = []
    for example_id in sorted(expected_ids):
        token_sets = [
            _candidate_tokens(indexed[example_id]) for indexed in indexed_runs
        ]
        scores = [
            _jaccard(token_sets[left], token_sets[right])
            for left, right in pair_indexes
        ]
        examples.append(
            ExampleStability(
                example_id=example_id,
                pair_count=len(scores),
                mean_jaccard=sum(scores) / len(scores),
                min_jaccard=min(scores),
            )
        )

    corpus_sets = [
        {
            (example_id, *candidate)
            for example_id, example in indexed.items()
            for candidate in _candidate_tokens(example)
        }
        for indexed in indexed_runs
    ]
    corpus_scores = [
        _jaccard(corpus_sets[left], corpus_sets[right])
        for left, right in pair_indexes
    ]
    example_means = [item.mean_jaccard for item in examples]
    example_mins = [item.min_jaccard for item in examples]
    return StabilityReport(
        run_count=len(runs),
        example_count=len(expected_ids),
        pair_count=len(pair_indexes),
        macro_mean_jaccard=(
            sum(example_means) / len(example_means) if example_means else 1.0
        ),
        macro_min_jaccard=min(example_mins) if example_mins else 1.0,
        corpus_mean_jaccard=sum(corpus_scores) / len(corpus_scores),
        corpus_min_jaccard=min(corpus_scores),
        examples=examples,
    )


def extraction_report_to_predictions(
    report: SkillExtractionReport,
) -> list[PredictedExample]:
    """실제 사용자 추출 보고서를 공통 Jaccard 입력 형식으로 변환한다."""
    return [
        PredictedExample(
            example_id=f"{chunk.source_kind}:{chunk.chunk_id}:{chunk.content_hash}",
            predicted=[
                ExpectedSkill(
                    extracted_name=skill.extracted_name,
                    category=skill.category,
                    level=skill.level,
                )
                for skill in chunk.skills
            ],
        )
        for chunk in report.chunks
    ]

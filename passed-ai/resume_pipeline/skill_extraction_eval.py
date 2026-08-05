"""스킬 후보 추출 결과의 정확 일치 Precision/Recall/F1 평가."""

from __future__ import annotations

from collections import defaultdict
import hashlib
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from .skill_extraction_models import SkillCategory
from .skill_extraction_models import ExtractableChunk
from .skill_extraction_worker import extract_chunk_candidates


class ExpectedSkill(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    extracted_name: str
    category: SkillCategory
    level: int = Field(ge=1, le=3)


class GoldenExample(BaseModel):
    model_config = ConfigDict(extra="forbid")
    example_id: str
    source_kind: str
    context_type: str
    content: str
    expected: list[ExpectedSkill]


class PredictedExample(BaseModel):
    model_config = ConfigDict(extra="forbid")
    example_id: str
    predicted: list[ExpectedSkill]


class MetricScore(BaseModel):
    true_positive: int
    false_positive: int
    false_negative: int
    precision: float
    recall: float
    f1: float


class LevelMetric(BaseModel):
    evaluated_count: int
    exact_matches: int
    accuracy: float
    mean_absolute_error: float


class OverExtractionMetric(BaseModel):
    negative_example_count: int
    false_positive_example_count: int
    false_positive_candidate_count: int
    over_extraction_rate: float


class EvaluationReport(BaseModel):
    micro: MetricScore
    by_category: dict[str, MetricScore]
    level: LevelMetric
    negative_examples: OverExtractionMetric
    missing_prediction_ids: list[str] = Field(default_factory=list)
    unknown_prediction_ids: list[str] = Field(default_factory=list)


def _key(skill: ExpectedSkill) -> tuple[str, str]:
    normalized_name = " ".join(skill.extracted_name.casefold().split())
    return normalized_name, skill.category.value


def _score(
    expected: set[tuple[str, ...]],
    predicted: set[tuple[str, ...]],
) -> MetricScore:
    tp = len(expected & predicted)
    fp = len(predicted - expected)
    fn = len(expected - predicted)
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = (
        2 * precision * recall / (precision + recall)
        if precision + recall
        else 0.0
    )
    return MetricScore(
        true_positive=tp,
        false_positive=fp,
        false_negative=fn,
        precision=precision,
        recall=recall,
        f1=f1,
    )


def evaluate_skill_predictions(
    golden: list[GoldenExample],
    predictions: list[PredictedExample],
) -> EvaluationReport:
    """예제별 집합을 합산해 micro 및 카테고리별 점수를 계산한다."""
    golden_by_id = {item.example_id: item for item in golden}
    predicted_by_id = {item.example_id: item for item in predictions}
    if len(golden_by_id) != len(golden):
        raise ValueError("골든셋 example_id가 중복되었습니다.")
    if len(predicted_by_id) != len(predictions):
        raise ValueError("예측 결과 example_id가 중복되었습니다.")

    expected_all: set[tuple[str, str, str]] = set()
    predicted_all: set[tuple[str, str, str]] = set()
    expected_by_category: dict[str, set[tuple[str, str]]] = defaultdict(set)
    predicted_by_category: dict[str, set[tuple[str, str]]] = defaultdict(set)
    expected_levels: dict[tuple[str, str, str], int] = {}
    predicted_levels: dict[tuple[str, str, str], int] = {}

    for example_id, example in golden_by_id.items():
        for skill in example.expected:
            name, category = _key(skill)
            expected_all.add((example_id, name, category))
            expected_by_category[category].add((example_id, name))
            expected_levels[(example_id, name, category)] = skill.level

        prediction = predicted_by_id.get(example_id)
        if prediction:
            for skill in prediction.predicted:
                name, category = _key(skill)
                predicted_all.add((example_id, name, category))
                predicted_by_category[category].add((example_id, name))
                predicted_levels[(example_id, name, category)] = skill.level

    matched_keys = expected_all & predicted_all
    absolute_errors = [
        abs(expected_levels[key] - predicted_levels[key]) for key in matched_keys
    ]
    level_exact_matches = sum(error == 0 for error in absolute_errors)

    negative_ids = {
        example.example_id for example in golden if not example.expected
    }
    false_positive_negative_ids = {
        example_id
        for example_id in negative_ids
        if predicted_by_id.get(example_id)
        and predicted_by_id[example_id].predicted
    }
    negative_candidate_count = sum(
        len(predicted_by_id[example_id].predicted)
        for example_id in false_positive_negative_ids
    )

    categories = [category.value for category in SkillCategory]
    return EvaluationReport(
        micro=_score(expected_all, predicted_all),
        by_category={
            category: _score(
                expected_by_category[category],
                predicted_by_category[category],
            )
            for category in categories
        },
        level=LevelMetric(
            evaluated_count=len(absolute_errors),
            exact_matches=level_exact_matches,
            accuracy=(
                level_exact_matches / len(absolute_errors)
                if absolute_errors
                else 0.0
            ),
            mean_absolute_error=(
                sum(absolute_errors) / len(absolute_errors)
                if absolute_errors
                else 0.0
            ),
        ),
        negative_examples=OverExtractionMetric(
            negative_example_count=len(negative_ids),
            false_positive_example_count=len(false_positive_negative_ids),
            false_positive_candidate_count=negative_candidate_count,
            over_extraction_rate=(
                len(false_positive_negative_ids) / len(negative_ids)
                if negative_ids
                else 0.0
            ),
        ),
        missing_prediction_ids=sorted(golden_by_id.keys() - predicted_by_id.keys()),
        unknown_prediction_ids=sorted(predicted_by_id.keys() - golden_by_id.keys()),
    )


def generate_golden_predictions(
    golden: list[GoldenExample],
    *,
    client: Any,
) -> list[PredictedExample]:
    """골든셋 본문을 실제 추출기와 같은 프롬프트 경로로 실행한다."""
    predictions: list[PredictedExample] = []
    for index, example in enumerate(golden, start=1):
        chunk = ExtractableChunk(
            source_kind=example.source_kind,
            chunk_id=index,
            context_type=example.context_type,
            chunk_content=example.content,
            content_hash=hashlib.sha256(example.content.encode("utf-8")).hexdigest(),
        )
        candidates = extract_chunk_candidates(chunk, client=client)
        predictions.append(
            PredictedExample(
                example_id=example.example_id,
                predicted=[
                    ExpectedSkill(
                        extracted_name=item.extracted_name,
                        category=item.category,
                        level=item.level,
                    )
                    for item in candidates
                ],
            )
        )
    return predictions


def load_golden_set(path: Path) -> list[GoldenExample]:
    return [GoldenExample.model_validate(item) for item in _load_json_array(path)]


def load_predictions(path: Path) -> list[PredictedExample]:
    return [PredictedExample.model_validate(item) for item in _load_json_array(path)]


def _load_json_array(path: Path) -> list[dict]:
    import json

    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError(f"JSON 최상위 값은 배열이어야 합니다: {path}")
    return raw

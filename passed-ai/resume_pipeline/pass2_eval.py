"""수동 감사로 만든 Pass 2 골든셋을 생성하고 preview 결과를 평가한다."""

from __future__ import annotations

from collections import defaultdict
import json
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from .skill_extraction_models import SkillCategory
from .skill_recall_worker import RecallExperimentReport


class Pass2GoldenCase(BaseModel):
    model_config = ConfigDict(extra="forbid")
    source_kind: str
    chunk_id: int
    skill_id: int
    skill_name: str
    category: SkillCategory
    content: str
    evidence: str
    expected: Literal["ACCEPT", "REJECT", "REVIEW"]
    reason: str | None = None


class BinaryMetric(BaseModel):
    true_positive: int = 0
    false_positive: int = 0
    false_negative: int = 0
    true_negative: int = 0
    precision: float = 0.0
    recall: float = 0.0
    f1: float = 0.0
    reject_accuracy: float = 0.0
    false_positive_rate: float = 0.0


class Pass2EvalReport(BaseModel):
    evaluated_count: int
    review_excluded_count: int
    metric: BinaryMetric
    by_category: dict[str, BinaryMetric] = Field(default_factory=dict)
    unknown_prediction_count: int
    unknown_predictions: list[dict] = Field(default_factory=list)


def build_golden_from_manual_audit(
    semantic_report_path: Path,
    audit_path: Path,
) -> list[Pass2GoldenCase]:
    """기존 78건 전수 감사 결과를 ACCEPT/REJECT/REVIEW 계약으로 고정한다.

    Q. ACCEPT 항목을 수동 감사 JSON에 다시 복사하지 않은 이유는 무엇인가요?
    A. 전수 감사 당시 전체 통과 집합에서 REJECT와 REVIEW를 명시했으므로 나머지가
       ACCEPT입니다. 원본 Pass 2 report와 감사 파일을 함께 사용해야 누락 없이 78건을
       재현할 수 있습니다.
    """
    report = RecallExperimentReport.model_validate_json(
        semantic_report_path.read_text(encoding="utf-8")
    )
    if report.pass2 is None:
        raise ValueError("semantic report에 Pass 2 결과가 없습니다.")
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
    labels: dict[tuple[str, int, str], tuple[str, str | None]] = {}
    for expected, field in (("REJECT", "rejected"), ("REVIEW", "review")):
        for item in audit[field]:
            labels[(item["source_kind"], item["chunk_id"], item["skill"])] = (
                expected,
                item.get("reason"),
            )

    content_by_chunk = {
        (chunk.source_kind, chunk.chunk_id): chunk.chunk_content
        for retrieval in report.retrievals
        for chunk in retrieval.chunks
    }
    golden: list[Pass2GoldenCase] = []
    for chunk in report.pass2.chunks:
        for verified in chunk.verified:
            expected, reason = labels.get(
                (chunk.source_kind, chunk.chunk_id, verified.name),
                ("ACCEPT", None),
            )
            golden.append(
                Pass2GoldenCase(
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    skill_id=verified.skill_id,
                    skill_name=verified.name,
                    category=verified.category,
                    content=content_by_chunk[(chunk.source_kind, chunk.chunk_id)],
                    evidence=verified.evidence,
                    expected=expected,
                    reason=reason,
                )
            )
    if len(golden) != audit["summary"]["verified_evidence_count"]:
        raise ValueError("감사 summary와 생성된 골든셋 건수가 다릅니다.")
    return golden


def load_pass2_golden(path: Path) -> list[Pass2GoldenCase]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    return [Pass2GoldenCase.model_validate(item) for item in raw]


def extend_golden_from_prediction_audit(
    golden: list[Pass2GoldenCase],
    prediction_path: Path,
    audit_path: Path,
) -> list[Pass2GoldenCase]:
    """기존 골든셋 밖 승인 건을 전수 감사 라벨로 추가한다."""
    report = RecallExperimentReport.model_validate_json(
        prediction_path.read_text(encoding="utf-8")
    )
    if report.pass2 is None:
        raise ValueError("prediction report에 Pass 2 결과가 없습니다.")
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
    labels = {
        (item["source_kind"], item["chunk_id"], item["skill_id"]): item
        for item in audit
    }
    existing = {
        (case.source_kind, case.chunk_id, case.skill_id) for case in golden
    }
    content_by_chunk = {
        (chunk.source_kind, chunk.chunk_id): chunk.chunk_content
        for retrieval in report.retrievals
        for chunk in retrieval.chunks
    }
    additions: list[Pass2GoldenCase] = []
    for chunk in report.pass2.chunks:
        for verified in chunk.verified:
            key = (chunk.source_kind, chunk.chunk_id, verified.skill_id)
            if key in existing:
                continue
            label = labels.get(key)
            if label is None:
                raise ValueError(f"골든셋 밖 승인 건의 감사 라벨이 없습니다: {key}")
            additions.append(
                Pass2GoldenCase(
                    source_kind=chunk.source_kind,
                    chunk_id=chunk.chunk_id,
                    skill_id=verified.skill_id,
                    skill_name=verified.name,
                    category=verified.category,
                    content=content_by_chunk[(chunk.source_kind, chunk.chunk_id)],
                    evidence=verified.evidence,
                    expected=label["expected"],
                    reason=label.get("reason"),
                )
            )
    if len(additions) != len(labels):
        raise ValueError(
            f"감사 라벨과 추가 건수가 다릅니다: labels={len(labels)} "
            f"additions={len(additions)}"
        )
    return [*golden, *additions]


def _metric(labels: list[bool], predictions: list[bool]) -> BinaryMetric:
    tp = sum(label and prediction for label, prediction in zip(labels, predictions))
    fp = sum(not label and prediction for label, prediction in zip(labels, predictions))
    fn = sum(label and not prediction for label, prediction in zip(labels, predictions))
    tn = sum(not label and not prediction for label, prediction in zip(labels, predictions))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    return BinaryMetric(
        true_positive=tp,
        false_positive=fp,
        false_negative=fn,
        true_negative=tn,
        precision=precision,
        recall=recall,
        f1=2 * precision * recall / (precision + recall)
        if precision + recall
        else 0.0,
        reject_accuracy=tn / (tn + fp) if tn + fp else 0.0,
        false_positive_rate=fp / (fp + tn) if fp + tn else 0.0,
    )


def evaluate_pass2_report(
    golden: list[Pass2GoldenCase],
    report: RecallExperimentReport,
) -> Pass2EvalReport:
    if report.pass2 is None:
        raise ValueError("평가할 Pass 2 결과가 없습니다.")
    predicted = {
        (chunk.source_kind, chunk.chunk_id, skill.skill_id)
        for chunk in report.pass2.chunks
        for skill in chunk.verified
    }
    golden_keys = {
        (case.source_kind, case.chunk_id, case.skill_id) for case in golden
    }
    evaluated = [case for case in golden if case.expected != "REVIEW"]
    labels = [case.expected == "ACCEPT" for case in evaluated]
    predictions = [
        (case.source_kind, case.chunk_id, case.skill_id) in predicted
        for case in evaluated
    ]
    category_rows: dict[SkillCategory, list[tuple[bool, bool]]] = defaultdict(list)
    for case, label, prediction in zip(evaluated, labels, predictions):
        category_rows[case.category].append((label, prediction))
    unknown = sorted(predicted - golden_keys)
    return Pass2EvalReport(
        evaluated_count=len(evaluated),
        review_excluded_count=len(golden) - len(evaluated),
        metric=_metric(labels, predictions),
        by_category={
            category.value: _metric(
                [label for label, _prediction in rows],
                [prediction for _label, prediction in rows],
            )
            for category, rows in category_rows.items()
        },
        unknown_prediction_count=len(unknown),
        unknown_predictions=[
            {"source_kind": source, "chunk_id": chunk_id, "skill_id": skill_id}
            for source, chunk_id, skill_id in unknown
        ],
    )

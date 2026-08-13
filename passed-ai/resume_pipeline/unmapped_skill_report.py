"""여러 mapping preview의 unmapped 후보를 운영 테이블 없이 집계한다."""

from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone

from pydantic import BaseModel, Field

from .skill_extraction_models import SkillCategory
from .skill_mapping_worker import (
    MappingFailureReason,
    normalize_skill_name_conservative,
)
from .user_skill_mapping_models import UnmappedEvidence, UserSkillMappingReport


class AggregatedUnmappedCandidate(BaseModel):
    normalized_name: str
    sample_extracted_name: str
    category: SkillCategory
    occurrence_count: int = Field(ge=1)
    sample_evidence: str
    failure_reasons: dict[str, int]


class UnmappedCandidateReport(BaseModel):
    generated_at: datetime
    input_report_count: int = Field(ge=1)
    total_occurrences: int = Field(ge=0)
    unique_candidates: int = Field(ge=0)
    candidates: list[AggregatedUnmappedCandidate]


def aggregate_unmapped_candidates(
    reports: list[UserSkillMappingReport],
) -> UnmappedCandidateReport:
    if not reports:
        raise ValueError("최소 한 개의 mapping report가 필요합니다.")

    grouped: dict[tuple[str, SkillCategory], list[UnmappedEvidence]] = defaultdict(list)
    for report in reports:
        for item in report.unmapped:
            key = (
                normalize_skill_name_conservative(item.extracted_name),
                item.category,
            )
            grouped[key].append(item)

    candidates: list[AggregatedUnmappedCandidate] = []
    for (normalized_name, category), items in grouped.items():
        reasons: dict[str, int] = defaultdict(int)
        for item in items:
            reason = (
                item.failure_reason.value
                if isinstance(item.failure_reason, MappingFailureReason)
                else str(item.failure_reason)
            )
            reasons[reason] += 1
        first = items[0]
        candidates.append(
            AggregatedUnmappedCandidate(
                normalized_name=normalized_name,
                sample_extracted_name=first.extracted_name,
                category=category,
                occurrence_count=len(items),
                sample_evidence=first.evidence,
                failure_reasons=dict(sorted(reasons.items())),
            )
        )
    candidates.sort(
        key=lambda item: (-item.occurrence_count, item.category.value, item.normalized_name)
    )
    return UnmappedCandidateReport(
        generated_at=datetime.now(timezone.utc),
        input_report_count=len(reports),
        total_occurrences=sum(len(report.unmapped) for report in reports),
        unique_candidates=len(candidates),
        candidates=candidates,
    )

from resume_pipeline.skill_extraction_models import SkillCategory
from resume_pipeline.skill_mapping_worker import MappingFailureReason
from resume_pipeline.unmapped_skill_report import aggregate_unmapped_candidates
from resume_pipeline.user_skill_mapping_models import (
    UnmappedEvidence,
    UserSkillMappingReport,
)


def _report(name: str) -> UserSkillMappingReport:
    return UserSkillMappingReport(
        user_id=1,
        extraction_model="test",
        skills=[],
        unmapped=[
            UnmappedEvidence(
                source_kind="RESUME",
                chunk_id=1,
                context_type="EXPERIENCE",
                extracted_name=name,
                category=SkillCategory.TECHNICAL_SKILL,
                evidence=f"{name}를 사용했습니다.",
                extracted_level=2,
                failure_reason=MappingFailureReason.LOW_SIMILARITY,
            )
        ],
    )


def test_unmapped_candidates_are_grouped_by_conservative_name_and_category():
    result = aggregate_unmapped_candidates(
        [_report("Vector   DB"), _report("vector db")]
    )

    assert result.total_occurrences == 2
    assert result.unique_candidates == 1
    assert result.candidates[0].normalized_name == "vector db"
    assert result.candidates[0].occurrence_count == 2

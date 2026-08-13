from resume_pipeline.pass2_eval import Pass2GoldenCase, evaluate_pass2_report
from resume_pipeline.skill_extraction_models import SkillCategory
from resume_pipeline.skill_recall_worker import (
    ChunkPass2Result,
    Pass2PreviewReport,
    RecallExperimentReport,
    VerifiedMissingSkill,
)


def _case(skill_id: int, expected: str) -> Pass2GoldenCase:
    return Pass2GoldenCase(
        source_kind="RESUME",
        chunk_id=1,
        skill_id=skill_id,
        skill_name=f"skill-{skill_id}",
        category=SkillCategory.TECHNICAL_SKILL,
        content="원문",
        evidence="원문",
        expected=expected,
    )


def test_pass2_eval_excludes_review_and_measures_reject_accuracy():
    report = RecallExperimentReport(
        extraction_model="gpt-4o-mini",
        pass1_skill_count=0,
        pass1_unmapped_count=0,
        retrievals=[],
        pass2=Pass2PreviewReport(
            model="gpt-4o-mini",
            verifier_mode="strict",
            chunks=[
                ChunkPass2Result(
                    source_kind="RESUME",
                    chunk_id=1,
                    content_hash="a" * 64,
                    proposed_count=3,
                    verified=[
                        VerifiedMissingSkill(
                            skill_id=1,
                            name="skill-1",
                            category=SkillCategory.TECHNICAL_SKILL,
                            evidence="원문",
                            level=1,
                            retrieval_similarity=0.8,
                        )
                    ],
                )
            ],
        ),
    )

    result = evaluate_pass2_report(
        [_case(1, "ACCEPT"), _case(2, "REJECT"), _case(3, "REVIEW")],
        report,
    )

    assert result.evaluated_count == 2
    assert result.review_excluded_count == 1
    assert result.metric.precision == 1.0
    assert result.metric.recall == 1.0
    assert result.metric.reject_accuracy == 1.0

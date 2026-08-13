from resume_pipeline.skill_extraction_eval import ExpectedSkill, PredictedExample
from resume_pipeline.skill_extraction_models import SkillCategory
from resume_pipeline.skill_extraction_models import (
    ExtractedChunkSkills,
    SkillCandidate,
    SkillExtractionReport,
)
from resume_pipeline.skill_stability_eval import (
    evaluate_prediction_stability,
    extraction_report_to_predictions,
)


def _run(java: bool, python: bool) -> list[PredictedExample]:
    predicted = []
    if java:
        predicted.append(
            ExpectedSkill(
                extracted_name="Java",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
            )
        )
    if python:
        predicted.append(
            ExpectedSkill(
                extracted_name="Python",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
            )
        )
    return [
        PredictedExample(example_id="positive", predicted=predicted),
        PredictedExample(example_id="negative", predicted=[]),
    ]


def test_stability_reports_pairwise_jaccard_and_treats_two_empty_sets_equal():
    report = evaluate_prediction_stability(
        [_run(True, True), _run(True, False), _run(True, True)]
    )

    assert report.run_count == 3
    assert report.pair_count == 3
    assert report.example_count == 2
    by_id = {item.example_id: item for item in report.examples}
    assert by_id["negative"].mean_jaccard == 1.0
    assert by_id["positive"].min_jaccard == 0.5
    assert report.corpus_min_jaccard == 0.5


def test_actual_extraction_report_uses_chunk_hash_as_stable_example_id():
    report = SkillExtractionReport(
        user_id=1,
        model="test-model",
        chunks=[
            ExtractedChunkSkills(
                source_kind="RESUME",
                chunk_id=7,
                context_type="EXPERIENCE",
                content_hash="a" * 64,
                skills=[
                    SkillCandidate(
                        extracted_name="Java",
                        category=SkillCategory.TECHNICAL_SKILL,
                        level=2,
                        evidence="Java를 사용했습니다.",
                    )
                ],
            )
        ],
    )

    converted = extraction_report_to_predictions(report)

    assert converted[0].example_id == f"RESUME:7:{'a' * 64}"
    assert converted[0].predicted[0].extracted_name == "Java"

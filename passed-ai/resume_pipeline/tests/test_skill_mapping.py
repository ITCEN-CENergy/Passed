from __future__ import annotations

import pytest

from resume_pipeline.skill_extraction_models import SkillCategory
from resume_pipeline.skill_mapping_models import (
    MappingExpectation,
    MappingMethod,
    SkillMappingGoldenCase,
)
from resume_pipeline.skill_mapping_worker import (
    MappingFailureReason,
    RawMappingResult,
    SimilarityHit,
    SkillAlias,
    SkillMaster,
    apply_mapping_thresholds,
    normalize_skill_name,
    normalize_skill_name_conservative,
    normalize_alias_candidate,
    normalize_alias_candidate_conservative,
    resolve_alias,
    resolve_exact_or_normalized,
)
from resume_pipeline.skill_mapping_name_only import (
    NameOnlyStrategy,
    _strategy,
    analyze_name_only_embeddings,
    embed_in_batches,
)
from resume_pipeline.skill_mapping_eval import evaluate_mapping_report
from resume_pipeline.skill_mapping_worker import RawMappingReport


def _case(name="Spring Boot", category=SkillCategory.TECHNICAL_SKILL):
    return SkillMappingGoldenCase(
        case_id="case-one",
        extracted_name=name,
        extracted_category=category,
        expectation=MappingExpectation.MAP,
        expected_skill_name="Spring Boot",
        expected_skill_category=SkillCategory.TECHNICAL_SKILL,
        allowed_mapping_methods=[MappingMethod.EXACT, MappingMethod.NORMALIZED],
        rationale="테스트",
    )


def _hit(name, category, similarity):
    return SimilarityHit(
        skill_id=1,
        name=name,
        category=category,
        similarity=similarity,
    )


def test_normalization_changes_only_surface_format():
    assert normalize_skill_name(" Spring-Boot ") == "springboot"
    assert normalize_skill_name("Ｓｐｒｉｎｇ　Ｂｏｏｔ") == "springboot"
    assert normalize_skill_name("React.js") != normalize_skill_name("React")
    assert normalize_skill_name("스프링부트") != normalize_skill_name("Spring Boot")


def test_conservative_normalization_keeps_meaningful_separators_and_collapses_spaces():
    assert normalize_skill_name_conservative(" Ｖｅｃｔｏｒ   DB ") == "vector db"
    assert normalize_skill_name_conservative("Spring-Boot") == "spring-boot"
    assert normalize_skill_name_conservative("Spring Boot") == "spring boot"


def test_certification_alias_normalization_removes_only_score_suffix():
    assert (
        normalize_alias_candidate("TOEIC 850점", SkillCategory.CERTIFICATION)
        == "toeic"
    )
    assert (
        normalize_alias_candidate("AWS S3", SkillCategory.TECHNICAL_SKILL)
        == "awss3"
    )
    assert (
        normalize_alias_candidate_conservative(
            "TOEIC 850점", SkillCategory.CERTIFICATION
        )
        == "toeic"
    )


def test_alias_resolution_is_category_scoped_and_rejects_ambiguity():
    case = SkillMappingGoldenCase(
        case_id="toeic-score",
        extracted_name="TOEIC 850점",
        extracted_category=SkillCategory.CERTIFICATION,
        expectation=MappingExpectation.MAP,
        expected_skill_name="TOEIC",
        expected_skill_category=SkillCategory.CERTIFICATION,
        allowed_mapping_methods=[MappingMethod.ALIAS],
        rationale="점수 접미사 제거",
    )
    unique = [
        SkillAlias(1, 10, "TOEIC", SkillCategory.CERTIFICATION, "TOEIC")
    ]
    resolved, _ = resolve_alias(case, unique)
    assert resolved is not None
    assert resolved.skill_name == "TOEIC"

    ambiguous = unique + [
        SkillAlias(2, 11, "다른 시험", SkillCategory.CERTIFICATION, "TOEIC")
    ]
    resolved, matches = resolve_alias(case, ambiguous)
    assert resolved is None
    assert len(matches) == 2


def test_exact_then_normalized_resolution_is_category_scoped():
    masters = [
        SkillMaster(1, "Spring Boot", SkillCategory.TECHNICAL_SKILL, True),
        SkillMaster(2, "springboot", SkillCategory.EXPERIENCE, True),
    ]

    exact_method, exact, _ = resolve_exact_or_normalized(_case(), masters)
    normalized_method, normalized, _ = resolve_exact_or_normalized(
        _case("spring-boot"), masters
    )

    assert exact_method is MappingMethod.EXACT
    assert exact.name == "Spring Boot"
    assert normalized_method is MappingMethod.NORMALIZED
    assert normalized.name == "Spring Boot"


@pytest.mark.parametrize(
    ("global_hits", "category_hits", "expected_reason"),
    [
        (
            [_hit("협업", SkillCategory.BEHAVIORAL_TRAIT, 0.82)],
            [_hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.70)],
            MappingFailureReason.CATEGORY_MISMATCH,
        ),
        (
            [_hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.70)],
            [_hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.70)],
            MappingFailureReason.LOW_SIMILARITY,
        ),
        (
            [
                _hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.80),
                _hit("일정 관리", SkillCategory.EXPERIENCE, 0.78),
            ],
            [
                _hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.80),
                _hit("일정 관리", SkillCategory.EXPERIENCE, 0.78),
            ],
            MappingFailureReason.AMBIGUOUS_MATCH,
        ),
    ],
)
def test_embedding_failure_reason_is_classified_into_three_types(
    global_hits, category_hits, expected_reason
):
    result = RawMappingResult(
        case_id="raw-one",
        expectation=MappingExpectation.MAP,
        extracted_name="일정 조율",
        extracted_category=SkillCategory.EXPERIENCE,
        global_top_k=global_hits,
        category_top_k=category_hits,
    )

    decision = apply_mapping_thresholds(
        result, min_similarity=0.75, min_margin=0.05
    )

    assert not decision.mapped
    assert decision.failure_reason is expected_reason


def test_embedding_maps_only_when_similarity_and_margin_pass():
    hits = [
        _hit("프로젝트 관리", SkillCategory.EXPERIENCE, 0.84),
        _hit("일정 관리", SkillCategory.EXPERIENCE, 0.76),
    ]
    result = RawMappingResult(
        case_id="raw-pass",
        expectation=MappingExpectation.MAP,
        extracted_name="프로젝트 진행 관리",
        extracted_category=SkillCategory.EXPERIENCE,
        global_top_k=hits,
        category_top_k=hits,
    )

    decision = apply_mapping_thresholds(
        result, min_similarity=0.75, min_margin=0.05
    )

    assert decision.mapped
    assert decision.method is MappingMethod.EMBEDDING
    assert decision.skill_name == "프로젝트 관리"


def test_name_only_strategy_uses_approved_top1_bands():
    assert _strategy(0.60)[0] is NameOnlyStrategy.NAME_ONLY
    assert _strategy(0.40)[0] is NameOnlyStrategy.NAME_ONLY_WITH_ALIASES
    assert _strategy(0.3999)[0] is NameOnlyStrategy.ALIAS_PRIMARY


def test_name_only_embedding_batches_preserve_order():
    calls = []

    def fake_embedder(texts):
        calls.append(list(texts))
        return [[float(int(text))] for text in texts]

    vectors = embed_in_batches(
        ["1", "2", "3", "4", "5"], fake_embedder, batch_size=2
    )

    assert calls == [["1", "2"], ["3", "4"], ["5"]]
    assert vectors == [[1.0], [2.0], [3.0], [4.0], [5.0]]


def test_name_only_experiment_is_read_only_and_reports_top1():
    masters = [
        SkillMaster(1, "협업", SkillCategory.BEHAVIORAL_TRAIT, True),
        SkillMaster(2, "의사소통", SkillCategory.BEHAVIORAL_TRAIT, True),
        SkillMaster(3, "Java", SkillCategory.TECHNICAL_SKILL, True),
    ]
    cases = [
        SkillMappingGoldenCase(
            case_id="semantic-map",
            extracted_name="역할 나눔",
            extracted_category=SkillCategory.BEHAVIORAL_TRAIT,
            expectation=MappingExpectation.MAP,
            expected_skill_name="협업",
            expected_skill_category=SkillCategory.BEHAVIORAL_TRAIT,
            allowed_mapping_methods=[MappingMethod.EMBEDDING],
            rationale="구체 행동을 협업으로 연결",
        ),
        SkillMappingGoldenCase(
            case_id="no-match",
            extracted_name="열정적인 인재",
            extracted_category=SkillCategory.BEHAVIORAL_TRAIT,
            expectation=MappingExpectation.NO_MATCH,
            rationale="근거 없는 자기평가",
        ),
    ]
    vectors = {
        "협업": [1.0, 0.0],
        "의사소통": [0.0, 1.0],
        "Java": [-1.0, 0.0],
        "역할 나눔": [0.9, 0.1],
        "열정적인 인재": [0.2, 0.8],
    }

    report = analyze_name_only_embeddings(
        masters,
        cases,
        lambda texts: [vectors[text] for text in texts],
        top_k=2,
        batch_size=2,
    )

    assert report.summary.expected_category_top1 == 1
    assert report.summary.expected_category_top1_rate == 1.0
    assert report.summary.recommended_strategy is NameOnlyStrategy.NAME_ONLY
    assert report.summary.no_match_top1_similarity.count == 1


def test_threshold_evaluation_counts_alias_coverage_and_no_match_rejection():
    map_case = SkillMappingGoldenCase(
        case_id="alias-map",
        extracted_name="역할 분담",
        extracted_category=SkillCategory.BEHAVIORAL_TRAIT,
        expectation=MappingExpectation.MAP,
        expected_skill_name="협업",
        expected_skill_category=SkillCategory.BEHAVIORAL_TRAIT,
        allowed_mapping_methods=[MappingMethod.ALIAS],
        rationale="별칭 매핑",
    )
    no_match_case = SkillMappingGoldenCase(
        case_id="reject-event",
        extracted_name="해커톤",
        extracted_category=SkillCategory.EXPERIENCE,
        expectation=MappingExpectation.NO_MATCH,
        rationale="행사명",
    )
    raw = RawMappingReport(
        model="test",
        top_k=2,
        total_cases=2,
        exact_resolved=0,
        normalized_resolved=0,
        alias_resolved=1,
        embedding_analyzed=1,
        category_mismatch_cases=0,
        missing_master_embeddings=0,
        results=[
            RawMappingResult(
                case_id="alias-map",
                expectation=MappingExpectation.MAP,
                extracted_name="역할 분담",
                extracted_category=SkillCategory.BEHAVIORAL_TRAIT,
                resolved_method=MappingMethod.ALIAS,
                resolved_skill_name="협업",
            ),
            RawMappingResult(
                case_id="reject-event",
                expectation=MappingExpectation.NO_MATCH,
                extracted_name="해커톤",
                extracted_category=SkillCategory.EXPERIENCE,
                category_top_k=[
                    _hit("매매", SkillCategory.EXPERIENCE, 0.42),
                    _hit("행사 운영", SkillCategory.EXPERIENCE, 0.40),
                ],
                global_top_k=[_hit("매매", SkillCategory.EXPERIENCE, 0.42)],
            ),
        ],
    )

    report = evaluate_mapping_report(
        raw, [map_case, no_match_case], min_similarity=0.75, min_margin=0.05
    )

    assert report.mapping_accuracy == 1.0
    assert report.mapping_coverage == 1.0
    assert report.unmapped_accuracy == 1.0
    assert report.by_method["ALIAS"].accuracy == 1.0

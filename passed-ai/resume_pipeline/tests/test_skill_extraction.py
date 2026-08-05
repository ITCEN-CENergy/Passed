from __future__ import annotations

from contextlib import contextmanager
from types import SimpleNamespace

import pytest
from tenacity import wait_none

import resume_pipeline.run_skill_extraction as extraction_cli
import resume_pipeline.skill_extraction_worker as extraction
from resume_pipeline.skill_extraction_eval import (
    ExpectedSkill,
    GoldenExample,
    PredictedExample,
    evaluate_skill_predictions,
    generate_golden_predictions,
    load_golden_set,
)
from resume_pipeline.skill_extraction_models import (
    ExtractableChunk,
    SkillCandidate,
    SkillCategory,
    SkillExtractionResponse,
)
from resume_pipeline.skill_extraction_prompt import build_user_prompt
from resume_pipeline.skill_mapping_models import (
    SkillMappingGoldenCase,
    load_mapping_golden_set,
)


class FakeExtractionCursor:
    def __init__(self, conn):
        self.conn = conn
        self.result = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql, params=()):
        compact = " ".join(sql.split())
        self.conn.executed.append((compact, params))
        self.result = (
            self.conn.resume_rows
            if "FROM resume_chunks" in compact
            else self.conn.cover_rows
        )

    def fetchall(self):
        return list(self.result)


class FakeExtractionConnection:
    def __init__(self, resume_rows=(), cover_rows=()):
        self.resume_rows = list(resume_rows)
        self.cover_rows = list(cover_rows)
        self.executed = []

    def cursor(self):
        return FakeExtractionCursor(self)


class FakeResponses:
    def __init__(self, outputs):
        self.outputs = list(outputs)
        self.calls = []

    def parse(self, **kwargs):
        self.calls.append(kwargs)
        output = self.outputs.pop(0)
        if isinstance(output, Exception):
            raise output
        return SimpleNamespace(output_parsed=output)


def _chunk(
    *,
    source_kind="RESUME",
    context_type="EXPERIENCE",
    content="Java와 Spring Boot로 API를 개발했습니다.",
    chunk_id=1,
):
    return ExtractableChunk(
        source_kind=source_kind,
        chunk_id=chunk_id,
        context_type=context_type,
        chunk_content=content,
        content_hash=f"hash-{chunk_id}",
    )


def test_prompt_routes_resume_certification_and_cover_motivation():
    certification = build_user_prompt(
        _chunk(context_type="CERTIFICATION", content="정보처리기사")
    )
    motivation = build_user_prompt(
        _chunk(
            source_kind="COVER_LETTER",
            context_type="MOTIVATION",
            content="백엔드 개발자가 되고 싶습니다.",
        )
    )

    assert "자격증만 보고 관련 기술의 실무 숙련도를" in certification
    assert "미래 포부와 희망은 제외" in motivation
    assert "필기 합격이나 준비 중인 자격은 제외" in certification
    assert "주된 내용이 미래 의도라면" in extraction.SYSTEM_PROMPT
    assert "행사·대회명은 그 자체로 후보가 아닙니다" in extraction.SYSTEM_PROMPT
    assert '"REST API", "API 설계", "API 개발"' in extraction.SYSTEM_PROMPT


def test_loads_only_worker_query_contract_with_context_types():
    conn = FakeExtractionConnection(
        resume_rows=[
            {
                "chunk_id": 1,
                "context_type": "EXPERIENCE",
                "chunk_content": "Java 개발",
                "content_hash": "r1",
            }
        ],
        cover_rows=[
            {
                "chunk_id": 2,
                "context_type": "PERSONALITY",
                "chunk_content": "의견을 조율했습니다.",
                "content_hash": "c1",
            }
        ],
    )

    chunks = extraction.load_extractable_chunks(conn, user_id=19)

    assert [(item.source_kind, item.context_type) for item in chunks] == [
        ("RESUME", "EXPERIENCE"),
        ("COVER_LETTER", "PERSONALITY"),
    ]
    executed_sql = " ".join(sql for sql, _ in conn.executed)
    assert "embedding_status = 'COMPLETED'" in executed_sql
    assert "embedding IS NOT NULL" in executed_sql


def test_structured_candidates_are_deduplicated_and_evidence_checked():
    response = SkillExtractionResponse(
        skills=[
            SkillCandidate(
                extracted_name="Java",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
                evidence="Java",
            ),
            SkillCandidate(
                extracted_name="Java",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
                evidence="Java",
            ),
            SkillCandidate(
                extracted_name="Kubernetes",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
                evidence="Kubernetes를 운영했습니다",
            ),
        ]
    )
    client = SimpleNamespace(responses=FakeResponses([response]))

    candidates = extraction.extract_chunk_candidates(_chunk(), client=client)

    assert [(item.extracted_name, item.category.value) for item in candidates] == [
        ("Java", "TECHNICAL_SKILL")
    ]


def test_normalized_candidate_name_is_kept_when_evidence_is_exact():
    response = SkillExtractionResponse(
        skills=[
            SkillCandidate(
                extracted_name="의사소통",
                category=SkillCategory.BEHAVIORAL_TRAIT,
                level=2,
                evidence="의견을 경청하고 조율했습니다",
            )
        ]
    )
    chunk = _chunk(
        source_kind="COVER_LETTER",
        context_type="PERSONALITY",
        content="의견을 경청하고 조율했습니다",
    )
    client = SimpleNamespace(responses=FakeResponses([response]))

    candidates = extraction.extract_chunk_candidates(chunk, client=client)

    assert [candidate.extracted_name for candidate in candidates] == ["의사소통"]


def test_future_only_evidence_is_excluded_but_completed_action_is_kept():
    response = SkillExtractionResponse(
        skills=[
            SkillCandidate(
                extracted_name="백엔드 개발",
                category=SkillCategory.EXPERIENCE,
                level=1,
                evidence="이 경험을 바탕으로 백엔드 개발자로 성장하고 싶습니다.",
            ),
            SkillCandidate(
                extracted_name="API 개발",
                category=SkillCategory.EXPERIENCE,
                level=2,
                evidence="API를 개발했고 입사 후에도 개선하겠습니다.",
            ),
        ]
    )
    content = (
        "이 경험을 바탕으로 백엔드 개발자로 성장하고 싶습니다. "
        "API를 개발했고 입사 후에도 개선하겠습니다."
    )
    client = SimpleNamespace(responses=FakeResponses([response]))

    candidates = extraction.extract_chunk_candidates(
        _chunk(
            source_kind="COVER_LETTER",
            context_type="EXPERIENCE",
            content=content,
        ),
        client=client,
    )

    assert [candidate.extracted_name for candidate in candidates] == ["API 개발"]


def test_certification_level_is_owned_value_one_only():
    with pytest.raises(ValueError, match="반드시 1"):
        SkillCandidate(
            extracted_name="정보처리기사",
            category=SkillCategory.CERTIFICATION,
            level=2,
            evidence="정보처리기사",
        )


def test_one_chunk_failure_does_not_stop_next_chunk(monkeypatch):
    chunks = [_chunk(chunk_id=1), _chunk(chunk_id=2, content="SQL을 최적화했습니다.")]
    success = SkillExtractionResponse(
        skills=[
            SkillCandidate(
                extracted_name="SQL",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
                evidence="SQL",
            )
        ]
    )
    responses = FakeResponses([RuntimeError("bad chunk"), success])
    client = SimpleNamespace(responses=responses)
    monkeypatch.setattr(extraction, "load_extractable_chunks", lambda conn, user_id: chunks)

    report = extraction.extract_user_skill_candidates(object(), 19, client=client)

    assert len(report.failures) == 1
    assert report.failures[0].chunk_id == 1
    assert report.candidate_count == 1
    assert report.chunks[0].chunk_id == 2


def test_transient_error_retries_three_times(monkeypatch):
    attempts = 0
    parsed = SkillExtractionResponse(skills=[])

    def flaky_request(client, chunk):
        nonlocal attempts
        attempts += 1
        if attempts < 3:
            raise extraction.TransientSkillExtractionError("temporary")
        return SimpleNamespace(output_parsed=parsed)

    monkeypatch.setattr(extraction, "_request_structured_extraction", flaky_request)
    monkeypatch.setattr(extraction, "wait_exponential", lambda **kwargs: wait_none())

    assert extraction.extract_chunk_candidates(_chunk(), client=object()) == []
    assert attempts == 3


def test_dry_run_does_not_create_openai_client(monkeypatch):
    fake_conn = object()

    @contextmanager
    def fake_connection():
        yield fake_conn

    monkeypatch.setattr(extraction_cli, "connection", fake_connection)
    monkeypatch.setattr(
        extraction_cli,
        "load_extractable_chunks",
        lambda conn, user_id: [_chunk(), _chunk(source_kind="COVER_LETTER")],
    )
    monkeypatch.setattr(
        extraction_cli,
        "extract_user_skill_candidates",
        lambda *args, **kwargs: pytest.fail("dry-run은 LLM 작업자를 호출하면 안 됩니다."),
    )
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    assert extraction_cli.main(["--user-id", "19", "--dry-run"]) == 0


def test_micro_precision_recall_f1_are_separate_from_mapping():
    golden = [
        GoldenExample(
            example_id="one",
            source_kind="RESUME",
            context_type="EXPERIENCE",
            content="text",
            expected=[
                ExpectedSkill(
                    extracted_name="Java",
                    category=SkillCategory.TECHNICAL_SKILL,
                    level=2,
                ),
                ExpectedSkill(
                    extracted_name="SQLD",
                    category=SkillCategory.CERTIFICATION,
                    level=1,
                ),
            ],
        )
    ]
    predictions = [
        PredictedExample(
            example_id="one",
            predicted=[
                ExpectedSkill(
                    extracted_name="java",
                    category=SkillCategory.TECHNICAL_SKILL,
                    level=3,
                ),
                ExpectedSkill(
                    extracted_name="Python",
                    category=SkillCategory.TECHNICAL_SKILL,
                    level=1,
                ),
            ],
        )
    ]

    report = evaluate_skill_predictions(golden, predictions)

    assert report.micro.precision == pytest.approx(0.5)
    assert report.micro.recall == pytest.approx(0.5)
    assert report.micro.f1 == pytest.approx(0.5)
    assert report.by_category["CERTIFICATION"].false_negative == 1
    assert report.level.evaluated_count == 1
    assert report.level.accuracy == 0.0
    assert report.level.mean_absolute_error == 1.0


def test_negative_example_over_extraction_is_reported_separately():
    golden = [
        GoldenExample(
            example_id="motivation",
            source_kind="COVER_LETTER",
            context_type="MOTIVATION",
            content="입사 후 전문가가 되겠습니다.",
            expected=[],
        )
    ]
    predictions = [
        PredictedExample(
            example_id="motivation",
            predicted=[
                ExpectedSkill(
                    extracted_name="전문가",
                    category=SkillCategory.EXPERIENCE,
                    level=1,
                )
            ],
        )
    ]

    report = evaluate_skill_predictions(golden, predictions)

    assert report.negative_examples.negative_example_count == 1
    assert report.negative_examples.false_positive_example_count == 1
    assert report.negative_examples.false_positive_candidate_count == 1
    assert report.negative_examples.over_extraction_rate == 1.0


def test_golden_predictions_use_same_chunk_extractor(monkeypatch):
    golden = [
        GoldenExample(
            example_id="gold-one",
            source_kind="RESUME",
            context_type="EXPERIENCE",
            content="Java로 개발했습니다.",
            expected=[],
        )
    ]
    monkeypatch.setattr(
        "resume_pipeline.skill_extraction_eval.extract_chunk_candidates",
        lambda chunk, client: [
            SkillCandidate(
                extracted_name="Java",
                category=SkillCategory.TECHNICAL_SKILL,
                level=2,
                evidence="Java",
            )
        ],
    )

    predictions = generate_golden_predictions(golden, client=object())

    assert predictions[0].example_id == "gold-one"
    assert predictions[0].predicted[0].extracted_name == "Java"
    assert predictions[0].predicted[0].level == 2


def test_draft_golden_set_is_valid():
    path = (
        extraction_cli.Path(__file__).resolve().parents[1]
        / "evaluation"
        / "golden_skill_extraction.json"
    )
    golden = load_golden_set(path)

    assert len(golden) >= 6
    assert any(not item.expected for item in golden)
    assert 25 <= len(golden) <= 35
    assert {skill.category for item in golden for skill in item.expected} == set(
        SkillCategory
    )


def test_golden_set_covers_known_regressions_and_hard_cases():
    path = (
        extraction_cli.Path(__file__).resolve().parents[1]
        / "evaluation"
        / "golden_skill_extraction.json"
    )
    golden = load_golden_set(path)
    by_id = {item.example_id: item for item in golden}

    # Q. 실제 실패 문장을 일반적인 미래 포부 예제와 별도로 고정하는 이유는 무엇인가요?
    # A. 추상적인 예제만 있으면 "이 경험을 바탕으로" 같은 회귀가 재발해도 테스트가
    #    통과할 수 있습니다. 운영에서 확인한 입력을 그대로 남겨 같은 버그를 막습니다.
    future = by_id["cover-motivation-future-only-001"]
    assert future.content == "이 경험을 바탕으로 백엔드 개발자로 성장하고 싶습니다."
    assert future.expected == []

    backend_names = {
        item.extracted_name
        for item in by_id["resume-experience-backend-001"].expected
    }
    assert "API 설계 및 개발" not in backend_names
    assert {"REST API", "API 설계", "API 개발"} <= backend_names

    assert {
        "resume-certification-toeic-001",
        "resume-certification-written-pass-001",
        "cover-certification-preparing-001",
        "resume-certification-multiple-001",
    } <= by_id.keys()

    technical_names = {
        item.extracted_name
        for example in golden
        for item in example.expected
        if item.category is SkillCategory.TECHNICAL_SKILL
    }
    assert {
        "스프링부트",
        "React.js",
        "AWS EC2",
        "AWS S3",
        "AWS RDS",
    } <= technical_names

    all_names = {
        item.extracted_name for example in golden for item in example.expected
    }
    assert "해커톤" not in all_names
    assert "소프트웨어 경진대회" not in all_names


def test_mapping_golden_set_is_separate_and_has_unmapped_controls():
    path = (
        extraction_cli.Path(__file__).resolve().parents[1]
        / "evaluation"
        / "golden_skill_mapping.json"
    )
    cases = load_mapping_golden_set(path)

    assert len(cases) == 47
    assert sum(case.should_map for case in cases) == 41
    assert sum(not case.should_map for case in cases) == 6
    by_id = {case.case_id: case for case in cases}
    assert by_id["map-teammate-listening"].expected_skill_name == "공감"
    assert by_id["map-api-development-alias"].expected_skill_name == "백엔드 API 개발"
    assert not by_id["unmapped-aws-ec2"].should_map


def test_mapping_golden_contract_rejects_ambiguous_unmapped_case():
    with pytest.raises(ValueError, match="should_map=false"):
        SkillMappingGoldenCase(
            case_id="bad-unmapped",
            extracted_name="없는 기술",
            extracted_category=SkillCategory.TECHNICAL_SKILL,
            should_map=False,
            expected_skill_name="Java",
            expected_skill_category=SkillCategory.TECHNICAL_SKILL,
            allowed_mapping_methods=[],
            rationale="실패 계약 테스트",
        )


def test_mapping_golden_targets_exist_in_skill_master_seed():
    evaluation_dir = extraction_cli.Path(__file__).resolve().parents[1] / "evaluation"
    cases = load_mapping_golden_set(evaluation_dir / "golden_skill_mapping.json")
    seed_path = (
        extraction_cli.Path(__file__).resolve().parents[3]
        / "passed-backend"
        / "src"
        / "main"
        / "resources"
        / "db"
        / "migration"
        / "V20260803111425482__seed_skills.sql"
    )
    seed_sql = seed_path.read_text(encoding="utf-8")

    # Q. 숫자 skill_id가 아니라 SQL 시드의 이름을 확인하는 이유는 무엇인가요?
    # A. IDENTITY 값은 DB마다 달라질 수 있지만 skills.name은 unique 계약입니다. 이름과
    #    카테고리를 함께 검사하면 팀원이 새 DB에서 실행해도 같은 정답을 검증할 수 있습니다.
    for case in cases:
        if not case.should_map:
            continue
        expected_row_prefix = (
            f"('{case.expected_skill_name}', "
            f"'{case.expected_skill_category.value}'"
        )
        assert expected_row_prefix in seed_sql, case.case_id

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
AI_ROOT = ROOT / "passed-ai"
REGRESSION_PATH = (
    AI_ROOT / "resume_pipeline" / "evaluation" / "job_4730_regression.json"
)
ALIAS_MIGRATION_PATH = (
    ROOT
    / "passed-backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V20260809110000000__add_ai_content_skill_aliases.sql"
)


def _regression() -> dict:
    return json.loads(REGRESSION_PATH.read_text(encoding="utf-8"))


def test_job_4730_baseline_and_all_missing_skills_are_frozen_once():
    regression = _regression()
    baseline = regression["baseline"]
    missing = regression["missing_skills"]

    assert regression["job_posting_id"] == 4730
    assert regression["user_id"] == 56
    assert baseline["processed_chunk_count"] == 19
    assert baseline["candidate_count"] == 71
    assert baseline["required_skill_count"] == 15
    assert baseline["required_coverage_rate"] == 0.4
    assert len(missing) == 22
    assert len({item["skill_id"] for item in missing}) == len(missing)
    assert {item["group"] for item in missing} == {"A", "B", "C"}


def test_job_4730_safe_aliases_recover_more_than_half_required_coverage():
    regression = _regression()
    baseline_ids = set(regression["baseline"]["required_owned_skill_ids"])
    recovered_ids = set(regression["expected_recovered_required_skill_ids"])
    required_count = regression["baseline"]["required_skill_count"]
    coverage = len(baseline_ids | recovered_ids) / required_count

    assert len(baseline_ids | recovered_ids) >= regression["acceptance"][
        "minimum_expected_required_owned_count"
    ]
    assert coverage >= regression["acceptance"][
        "minimum_required_coverage_rate"
    ]


def test_job_4730_alias_migration_contains_only_reviewed_explicit_variants():
    sql = ALIAS_MIGRATION_PATH.read_text(encoding="utf-8")
    expected_pairs = {
        ("벡터 데이터베이스", "Vector DB"),
        ("Azure", "Microsoft Azure"),
        ("Microsoft Azure AI Engineer Associate", "Azure AI Engineer Associate"),
        ("서비스 모니터링", "서비스 배포 및 모니터링"),
        ("콘텐츠 생성", "콘텐츠 생성 서비스 구현"),
        ("콘텐츠 생성 프로젝트", "AI 기반 콘텐츠 생성 플랫폼 개발"),
        ("기술 부채 개선", "기술 부채 정의"),
        ("장애 원인 분석", "장애 원인 추적"),
    }
    for skill_name, alias in expected_pairs:
        assert f"'{skill_name}'" in sql
        assert f"'{alias}'" in sql

    # 관계 검토 대상은 alias로 억지 통합하지 않는다.
    assert "('보안', 'TECHNICAL_SKILL', '개인정보 보호'" not in sql
    assert "('LLM', 'TECHNICAL_SKILL', 'LLM API'" not in sql

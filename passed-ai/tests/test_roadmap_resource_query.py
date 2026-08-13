import pytest

from api.features.roadmap.resource_query import (
    _build_queries_sync,
    build_competency_ranking_contexts,
    build_contextual_search_queries,
)
from api.features.roadmap.schema import Competency, CompetencySource


def _competency() -> Competency:
    return Competency(
        roadmapSkillKey="incident-prevention",
        standardCompetencyId=1147,
        standardCompetencyName="장애 재발 방지",
        category="EXPERIENCE",
        currentLevel=1,
        targetLevel=2,
        requirementType="REQUIRED",
        gapLevel=1,
        frequency=1,
        priority=1,
        sources=[CompetencySource(jobPostingId=4730)],
    )


def test_query_combines_skill_description_and_job_posting_context(monkeypatch):
    monkeypatch.setattr(
        "api.features.roadmap.resource_query._load_search_context",
        lambda skill_ids, posting_ids: (
            {1147: "서비스 장애 원인을 분석하고 재발 방지 대책을 수립"},
            {4730: "백엔드 개발자 대규모 소프트웨어 시스템 운영"},
        ),
    )

    query = _build_queries_sync([_competency()])["incident-prevention"]

    assert "장애 재발 방지" in query
    assert "서비스 장애 원인" in query
    assert "백엔드 개발자" in query
    assert "소프트웨어 시스템 운영" in query
    assert "불안장애" not in query


@pytest.mark.asyncio
async def test_query_falls_back_to_request_data_when_database_fails(monkeypatch):
    monkeypatch.setattr(
        "api.features.roadmap.resource_query._build_queries_sync",
        lambda competencies: (_ for _ in ()).throw(RuntimeError("db unavailable")),
    )

    query = (await build_contextual_search_queries([_competency()]))[
        "incident-prevention"
    ]

    assert query == "장애 재발 방지 EXPERIENCE 학습 가이드 실무 실습"


@pytest.mark.asyncio
async def test_ranking_context_combines_skill_and_job_context(monkeypatch) -> None:
    monkeypatch.setattr(
        "api.features.roadmap.resource_query._load_search_context",
        lambda skill_ids, posting_ids: (
            {1147: "서비스 로그와 메트릭으로 장애 원인을 추적하는 역량"},
            {4730: "백엔드 서비스 운영 및 장애 대응 담당"},
        ),
    )

    context = (await build_competency_ranking_contexts([_competency()]))[
        "incident-prevention"
    ]

    assert "서비스 로그와 메트릭" in context
    assert "백엔드 서비스 운영" in context


@pytest.mark.asyncio
async def test_ranking_context_uses_peer_competencies_when_database_fails(
    monkeypatch,
) -> None:
    monkeypatch.setattr(
        "api.features.roadmap.resource_query._load_search_context",
        lambda skill_ids, posting_ids: (_ for _ in ()).throw(
            RuntimeError("db unavailable")
        ),
    )
    javascript = _competency().model_copy(update={
        "roadmapSkillKey": "javascript",
        "standardCompetencyId": 96,
        "standardCompetencyName": "JavaScript",
    })

    contexts = await build_competency_ranking_contexts([
        _competency(), javascript
    ])

    assert "연관 역량" in contexts["incident-prevention"]
    assert "JavaScript" in contexts["incident-prevention"]

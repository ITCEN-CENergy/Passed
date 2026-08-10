from copy import deepcopy
import logging
import os

import pytest
from fastapi.testclient import TestClient

os.environ["ROADMAP_GENERATOR"] = "fake"
os.environ["ROADMAP_RESOURCE_SEARCH_ENABLED"] = "false"

from app.main import app
from api.features.roadmap.planner import create_learning_stages
from api.features.roadmap.schema import LearningResource
from api.features.roadmap.schema import (
    ModelGeneratedSkillContent,
    ModelGeneratedTwoStageRoadmapContent,
    RoadmapGenerateRequest,
)
from api.features.roadmap.service import (
    FakeRoadmapContentGenerator,
    generate_roadmap,
)


client = TestClient(app)


def competency(
    *,
    key: str = "competency-1",
    name: str = "Docker",
    category: str = "TECHNICAL_SKILL",
    current_level: int = 1,
    target_level: int = 3,
) -> dict:
    return {
        "roadmapSkillKey": key,
        "standardCompetencyId": 1,
        "standardCompetencyName": name,
        "category": category,
        "currentLevel": current_level,
        "targetLevel": target_level,
        "requirementType": "REQUIRED",
        "gapLevel": max(target_level - current_level, 0),
        "frequency": 2,
        "priority": 1,
        "sources": [
            {
                "jobPostingId": 101,
                "currentEvidence": f"{name} 학습 및 실습 경험",
            }
        ],
    }


def request_with(*competencies: dict) -> dict:
    return {"userId": 10, "competencies": list(competencies)}


def test_generate_normal_request() -> None:
    response = client.post(
        "/api/v1/roadmaps/generate", json=request_with(competency())
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Docker 학습 로드맵"
    assert body["skills"][0]["roadmapSkillKey"] == "competency-1"
    assert len(body["skills"][0]["milestones"]) == 6


def test_generate_multiple_competencies_in_request_order() -> None:
    payload = request_with(
        competency(key="docker", current_level=1, target_level=3),
        competency(key="aws", name="AWS", current_level=2, target_level=3),
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 200
    assert [skill["roadmapSkillKey"] for skill in response.json()["skills"]] == [
        "docker",
        "aws",
    ]
    assert response.json()["title"] == "Docker·AWS 중심 직무 역량 강화 로드맵"


@pytest.mark.asyncio
async def test_content_generation_runs_per_competency_with_global_concurrency() -> None:
    class RecordingGenerator(FakeRoadmapContentGenerator):
        def __init__(self) -> None:
            self.batch_sizes: list[int] = []
            self.stage_sizes: list[int] = []

        async def generate(self, competencies, stages_by_key, resources_by_key):
            self.batch_sizes.append(len(competencies))
            self.stage_sizes.append(len(next(iter(stages_by_key.values()))))
            return await super().generate(competencies, stages_by_key, resources_by_key)

    generator = RecordingGenerator()
    items = [
        competency(key=f"competency-{index}", name=f"Skill {index}")
        for index in range(1, 11)
    ]
    request = RoadmapGenerateRequest.model_validate(request_with(*items))

    response = await generate_roadmap(request, generator=generator)

    assert generator.batch_sizes == [1] * 10
    assert generator.stage_sizes == [2] * 10
    assert [skill.roadmapSkillKey for skill in response.skills] == [
        f"competency-{index}" for index in range(1, 11)
    ]


@pytest.mark.asyncio
async def test_resource_search_runs_after_milestone_generation(monkeypatch) -> None:
    state = {"generated": False, "searches": 0}

    class TrackingGenerator(FakeRoadmapContentGenerator):
        async def generate(self, competencies, stages_by_key, resources_by_key):
            assert resources_by_key == {
                competencies[0].roadmapSkillKey: []
            }
            result = await super().generate(
                competencies, stages_by_key, resources_by_key
            )
            state["generated"] = True
            return result

    async def search_after_generation(
        self, competency, search_query=None, provider_queries=None
    ):
        assert state["generated"] is True
        assert "Docker" in search_query
        state["searches"] += 1
        return []

    monkeypatch.setattr(
        "api.features.roadmap.service.LearningResourceSearchService.search",
        search_after_generation,
    )
    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(current_level=1, target_level=3))
    )

    await generate_roadmap(request, generator=TrackingGenerator())

    assert state["searches"] == 6


@pytest.mark.asyncio
async def test_invalid_competency_batch_falls_back_to_per_stage(caplog) -> None:
    class MissingStageGenerator(FakeRoadmapContentGenerator):
        def __init__(self) -> None:
            self.stage_sizes: list[int] = []

        async def generate(self, competencies, stages_by_key, resources_by_key):
            stages = next(iter(stages_by_key.values()))
            self.stage_sizes.append(len(stages))
            generated = await super().generate(
                competencies, stages_by_key, resources_by_key
            )
            if len(stages) > 1:
                generated.skills[0].stages = generated.skills[0].stages[:1]
            return generated

    generator = MissingStageGenerator()
    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(current_level=1, target_level=3))
    )

    with caplog.at_level(logging.WARNING):
        response = await generate_roadmap(request, generator=generator)

    assert generator.stage_sizes == [2, 1, 1]
    assert len(response.skills[0].milestones) == 6
    fallback_records = [
        record for record in caplog.records
        if getattr(record, "event", None) == "roadmap_competency_batch_fallback"
    ]
    assert len(fallback_records) == 1
    assert fallback_records[0].competencyKey == "competency-1"


@pytest.mark.asyncio
async def test_external_generation_failure_does_not_trigger_fallback() -> None:
    class UnavailableGenerator(FakeRoadmapContentGenerator):
        def __init__(self) -> None:
            self.call_count = 0

        async def generate(self, competencies, stages_by_key, resources_by_key):
            self.call_count += 1
            raise ConnectionError("model unavailable")

    generator = UnavailableGenerator()
    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(current_level=1, target_level=3))
    )

    with pytest.raises(ConnectionError, match="model unavailable"):
        await generate_roadmap(request, generator=generator)

    assert generator.call_count == 1


@pytest.mark.asyncio
async def test_model_schema_excludes_key_and_business_logic_binds_it() -> None:
    generator = FakeRoadmapContentGenerator()
    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(key="expected-key"))
    )

    response = await generate_roadmap(request, generator=generator)

    assert "roadmapSkillKey" not in ModelGeneratedSkillContent.model_fields
    assert response.skills[0].roadmapSkillKey == "expected-key"


@pytest.mark.asyncio
async def test_two_stage_model_schema_rejects_a_missing_stage() -> None:
    generator = FakeRoadmapContentGenerator()
    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(current_level=1, target_level=2))
    )
    generated = await generator.generate(
        request.competencies,
        {"competency-1": [create_learning_stages(request.competencies[0])[0]]},
        {},
    )

    with pytest.raises(ValueError):
        ModelGeneratedTwoStageRoadmapContent.model_validate(generated.model_dump())


@pytest.mark.asyncio
async def test_same_milestone_titles_are_allowed_in_different_stages() -> None:
    class RepeatingTitleGenerator(FakeRoadmapContentGenerator):
        def _contents(self, competency, stage, resources):
            contents = super()._contents(competency, stage, resources)
            for content, title in zip(contents, ["핵심 개념", "단계별 실습", "실전 과제"]):
                content.title = title
            return contents

    request = RoadmapGenerateRequest.model_validate(
        request_with(competency(current_level=1, target_level=3))
    )

    response = await generate_roadmap(request, generator=RepeatingTitleGenerator())

    assert [item.title for item in response.skills[0].milestones] == [
        "핵심 개념", "단계별 실습", "실전 과제",
        "핵심 개념", "단계별 실습", "실전 과제",
    ]


@pytest.mark.parametrize(
    ("current_level", "target_level", "expected_pairs"),
    [
        (1, 2, [(1, 2), (1, 2), (1, 2)]),
        (2, 3, [(2, 3), (2, 3), (2, 3)]),
        (1, 3, [(1, 2), (1, 2), (1, 2), (2, 3), (2, 3), (2, 3)]),
        (3, 3, [(3, 3), (3, 3), (3, 3)]),
    ],
)
def test_milestones_connect_each_level(
    current_level: int, target_level: int, expected_pairs: list[tuple[int, int]]
) -> None:
    payload = request_with(
        competency(current_level=current_level, target_level=target_level)
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    milestones = response.json()["skills"][0]["milestones"]
    assert [(item["startLevel"], item["targetLevel"]) for item in milestones] == expected_pairs
    assert [item["learningOrder"] for item in milestones] == list(
        range(1, len(milestones) + 1)
    )
    assert all(item["estimatedMinutes"] >= 1 for item in milestones)


def test_general_milestone_type_difficulty_and_time_are_deterministic() -> None:
    payload = request_with(competency(current_level=1, target_level=3))

    milestones = client.post(
        "/api/v1/roadmaps/generate", json=payload
    ).json()["skills"][0]["milestones"]

    assert [item["milestoneType"] for item in milestones] == [
        "PRACTICE",
        "PRACTICE",
        "PRACTICE",
        "PROJECT",
        "PROJECT",
        "PROJECT",
    ]
    assert [item["difficulty"] for item in milestones] == [
        "INTERMEDIATE",
        "INTERMEDIATE",
        "INTERMEDIATE",
        "ADVANCED",
        "ADVANCED",
        "ADVANCED",
    ]
    assert [item["estimatedMinutes"] for item in milestones] == [
        120, 120, 120, 180, 180, 180
    ]


def test_certification_can_have_multiple_milestones_in_fixed_level_stage() -> None:
    payload = request_with(
        competency(
            key="certification-1",
            name="SQLD",
            category="CERTIFICATION",
            current_level=0,
            target_level=1,
        )
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 200
    milestones = response.json()["skills"][0]["milestones"]
    assert len(milestones) == 3
    assert all(item["startLevel"] == 0 for item in milestones)
    assert all(item["targetLevel"] == 1 for item in milestones)
    assert all(item["milestoneType"] == "CERTIFICATION" for item in milestones)
    assert [item["learningOrder"] for item in milestones] == [1, 2, 3]


def test_achieved_certification_gets_reinforcement_milestones() -> None:
    payload = request_with(
        competency(
            key="certification-1",
            name="SQLD",
            category="CERTIFICATION",
            current_level=1,
            target_level=1,
        )
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 200
    milestones = response.json()["skills"][0]["milestones"]
    assert len(milestones) == 3
    assert all(item["startLevel"] == item["targetLevel"] == 1 for item in milestones)
    assert all(item["milestoneType"] == "CERTIFICATION" for item in milestones)


def test_same_request_returns_same_response() -> None:
    payload = request_with(competency(current_level=1, target_level=3))

    first = client.post("/api/v1/roadmaps/generate", json=deepcopy(payload))
    second = client.post("/api/v1/roadmaps/generate", json=deepcopy(payload))

    assert first.status_code == second.status_code == 200
    assert first.json() == second.json()


def test_nullable_current_evidence_is_accepted() -> None:
    item = competency()
    item["sources"][0]["currentEvidence"] = None

    response = client.post("/api/v1/roadmaps/generate", json=request_with(item))

    assert response.status_code == 200


def test_non_certification_can_start_from_level_zero() -> None:
    response = client.post(
        "/api/v1/roadmaps/generate",
        json=request_with(competency(current_level=0, target_level=2)),
    )

    assert response.status_code == 200
    milestones = response.json()["skills"][0]["milestones"]
    assert [(item["startLevel"], item["targetLevel"]) for item in milestones] == [
        (0, 1), (0, 1), (0, 1),
        (1, 2), (1, 2), (1, 2),
    ]


@pytest.mark.parametrize(
    "payload",
    [
        {"userId": 10, "competencies": []},
        request_with(competency(current_level=3, target_level=1)),
        request_with(competency(current_level=-1, target_level=1)),
        request_with(competency(current_level=0, target_level=6)),
        request_with(competency(name="   ")),
    ],
)
def test_invalid_request_returns_422(payload: dict) -> None:
    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 422


def test_router_is_registered() -> None:
    paths = app.openapi()["paths"]

    assert "/api/v1/roadmaps/generate" in paths


def test_application_reuses_and_closes_shared_http_client() -> None:
    with TestClient(app) as lifespan_client:
        shared_client = app.state.http_client

        first = lifespan_client.post(
            "/api/v1/roadmaps/generate", json=request_with(competency())
        )
        second = lifespan_client.post(
            "/api/v1/roadmaps/generate", json=request_with(competency())
        )

        assert first.status_code == 200
        assert second.status_code == 200
        assert app.state.http_client is shared_client
        assert not shared_client.is_closed

    assert shared_client.is_closed


def test_http_client_loggers_do_not_expose_request_urls() -> None:
    assert logging.getLogger("httpx").getEffectiveLevel() >= logging.WARNING
    assert logging.getLogger("httpcore").getEffectiveLevel() >= logging.WARNING
    assert logging.getLogger("openai").getEffectiveLevel() >= logging.WARNING


def test_generation_records_baseline_metrics(caplog) -> None:
    with caplog.at_level(logging.INFO):
        response = client.post(
            "/api/v1/roadmaps/generate",
            json=request_with(competency(current_level=1, target_level=2)),
        )

    assert response.status_code == 200
    records_by_event = {
        record.event: record
        for record in caplog.records
        if hasattr(record, "event")
    }
    expected_events = {
        "roadmap_generation_started",
        "roadmap_resource_search_skipped",
        "roadmap_resource_search_completed",
        "roadmap_content_generation_completed",
        "roadmap_generation_completed",
    }
    assert expected_events <= records_by_event.keys()

    generation_ids = {
        record.generationId
        for record in records_by_event.values()
        if hasattr(record, "generationId")
    }
    assert len(generation_ids) == 1
    assert records_by_event["roadmap_generation_completed"].status == "SUCCESS"
    assert records_by_event["roadmap_generation_completed"].elapsedMs >= 0
    assert records_by_event["roadmap_resource_search_completed"].resultCount == 0
    assert records_by_event["roadmap_content_generation_completed"].generator == (
        "FakeRoadmapContentGenerator"
    )


def test_resource_description_is_milestone_recommendation_reason(monkeypatch) -> None:
    resource = LearningResource(
        resourceId="book-1",
        resourceType="BOOK",
        title="Docker 실전 가이드",
        description="검색 API가 반환한 원문 소개",
        provider="테스트 출판사",
        url="https://example.com/docker",
        authors=["홍길동"],
        isFree=None,
    )
    async def search_resources(
        self, competency, search_query=None, provider_queries=None
    ):
        return [resource]

    monkeypatch.setattr(
        "api.features.roadmap.service.LearningResourceSearchService.search",
        search_resources,
    )

    response = client.post(
        "/api/v1/roadmaps/generate",
        json=request_with(competency(current_level=1, target_level=2)),
    )

    descriptions = [
        item["learningResources"][0]["description"]
        for item in response.json()["skills"][0]["milestones"]
    ]
    assert all("완료 기준을 점검" in value for value in descriptions)
    assert all(value != "검색 API가 반환한 원문 소개" for value in descriptions)

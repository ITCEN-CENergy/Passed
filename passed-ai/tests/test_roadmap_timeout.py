import asyncio

import httpx
import pytest
from fastapi import HTTPException

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.router import generate as generate_route
from api.features.roadmap.schema import (
    Competency,
    ModelGeneratedRoadmapContent,
    LearningResource,
    LearningStage,
    RoadmapGenerateRequest,
)
from api.features.roadmap.service import generate_roadmap


def _request() -> RoadmapGenerateRequest:
    return RoadmapGenerateRequest(
        userId=1,
        competencies=[Competency(
            roadmapSkillKey="docker",
            standardCompetencyId=1,
            standardCompetencyName="Docker",
            category="TECHNICAL_SKILL",
            currentLevel=1,
            targetLevel=2,
            requirementType="REQUIRED",
            gapLevel=1,
            frequency=1,
            priority=1,
        )],
    )


def _settings(*, search_enabled: bool) -> RoadmapSettings:
    return RoadmapSettings(
        ROADMAP_GENERATOR="fake",
        ROADMAP_RESOURCE_SEARCH_ENABLED=search_enabled,
        ROADMAP_GENERATION_TOTAL_TIMEOUT_SECONDS=0.01,
        KMOOC_SERVICE_KEY=None,
        KAKAO_REST_API_KEY=None,
        KEENABLE_SEARCH_ENABLED=False,
    )


class NeverEndingProvider:
    name = "never_ending"

    def __init__(self) -> None:
        self.cancelled = False

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]:
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            self.cancelled = True
            raise


class NeverEndingGenerator:
    def __init__(self) -> None:
        self.cancelled = False

    async def generate(
        self,
        competencies: list[Competency],
        stages_by_key: dict[str, list[LearningStage]],
        resources_by_key: dict[str, list[LearningResource]],
    ) -> ModelGeneratedRoadmapContent:
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            self.cancelled = True
            raise


@pytest.mark.asyncio
async def test_generation_deadline_cancels_pending_search(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    provider = NeverEndingProvider()
    monkeypatch.setattr(
        "api.features.roadmap.service.get_roadmap_settings",
        lambda: _settings(search_enabled=True),
    )
    monkeypatch.setattr(
        "api.features.roadmap.resources.recommendation.create_resource_providers",
        lambda client, settings: (provider,),
    )

    async with httpx.AsyncClient() as client:
        with pytest.raises(TimeoutError):
            await generate_roadmap(_request(), http_client=client)

    assert provider.cancelled is True


@pytest.mark.asyncio
async def test_generation_deadline_cancels_pending_generator(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    generator = NeverEndingGenerator()
    monkeypatch.setattr(
        "api.features.roadmap.service.get_roadmap_settings",
        lambda: _settings(search_enabled=False),
    )

    async with httpx.AsyncClient() as client:
        with pytest.raises(TimeoutError):
            await generate_roadmap(
                _request(), generator=generator, http_client=client
            )

    assert generator.cancelled is True


@pytest.mark.asyncio
async def test_generation_deadline_is_returned_as_504(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def timed_out(*args, **kwargs):
        raise TimeoutError

    monkeypatch.setattr(
        "api.features.roadmap.router.generate_roadmap",
        timed_out,
    )

    async with httpx.AsyncClient() as client:
        with pytest.raises(HTTPException) as raised:
            await generate_route(_request(), client)

    assert raised.value.status_code == 504
    assert raised.value.detail == "roadmap generation timed out"

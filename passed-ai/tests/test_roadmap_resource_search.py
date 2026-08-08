import asyncio
import logging

import httpx
import pytest

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resource_provider import create_resource_providers
from api.features.roadmap.resource_search import (
    LearningResourceSearchService,
    _official_provider,
    _summarize,
)
from api.features.roadmap.schema import Competency, LearningResource


class TrackingProvider:
    def __init__(self, name: str, delay: float, tracker: dict[str, int]) -> None:
        self.name = name
        self._delay = delay
        self._tracker = tracker

    async def search(self, competency: Competency) -> list[LearningResource]:
        self._tracker["active"] += 1
        self._tracker["maximum"] = max(
            self._tracker["maximum"], self._tracker["active"]
        )
        try:
            await asyncio.sleep(self._delay)
            return [LearningResource(
                resourceId=f"{self.name}-{competency.roadmapSkillKey}",
                resourceType="WEB_RESOURCE",
                title=self.name,
                provider=self.name,
                url=f"https://example.com/{self.name}/{competency.roadmapSkillKey}",
            )]
        finally:
            self._tracker["active"] -= 1


def _competency() -> Competency:
    return Competency(
        roadmapSkillKey="docker",
        standardCompetencyId=1,
        standardCompetencyName="Docker",
        category="TECHNICAL_SKILL",
        currentLevel=1,
        targetLevel=2,
        requirementType="REQUIRED",
        gapLevel=1,
        frequency=2,
        priority=1,
        sources=[],
    )


@pytest.mark.asyncio
async def test_kakao_book_response_is_normalized() -> None:
    def handle_request(request: httpx.Request) -> httpx.Response:
        assert str(request.url).startswith("https://dapi.kakao.com/v3/search/book")
        return httpx.Response(200, request=request, json={
            "documents": [{
                "title": "<b>Docker</b> 실전 가이드",
                "contents": "컨테이너 실습",
                "url": "https://example.com/book",
                "isbn": "1234",
                "authors": ["홍길동"],
                "publisher": "테스트 출판사",
                "thumbnail": "https://example.com/book.jpg",
            }]
        })

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle_request)) as client:
        resources = await LearningResourceSearchService(
            create_resource_providers(client, settings), enabled=True
        ).search(_competency())

    assert len(resources) == 1
    assert resources[0].resourceType == "BOOK"
    assert resources[0].title == "Docker 실전 가이드"
    assert resources[0].authors == ["홍길동"]


@pytest.mark.asyncio
async def test_provider_failure_does_not_fail_search(caplog) -> None:
    def failed_get(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("unavailable")

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    with caplog.at_level(logging.INFO):
        async with httpx.AsyncClient(transport=httpx.MockTransport(failed_get)) as client:
            assert await LearningResourceSearchService(
                create_resource_providers(client, settings),
                enabled=True,
                generation_id="generation-1",
            ).search(_competency()) == []

    provider_records = [
        record for record in caplog.records
        if getattr(record, "event", None) == "roadmap_provider_search_completed"
    ]
    failed_record = next(
        record for record in provider_records if record.provider == "kakao_book"
    )
    assert failed_record.generationId == "generation-1"
    assert failed_record.competencyKey == "docker"
    assert failed_record.status == "FAILED"
    assert failed_record.errorType == "ConnectError"
    assert failed_record.resultCount == 0
    assert failed_record.elapsedMs >= 0

    empty_providers = {
        record.provider for record in provider_records if record.status == "EMPTY"
    }
    assert empty_providers == {"kmooc", "tavily"}


@pytest.mark.asyncio
async def test_provider_calls_respect_configured_concurrency() -> None:
    tracker = {"active": 0, "maximum": 0}
    providers = tuple(
        TrackingProvider(f"provider-{index}", 0.01, tracker)
        for index in range(3)
    )
    service = LearningResourceSearchService(
        providers,
        enabled=True,
        max_concurrency=2,
    )

    await asyncio.gather(
        service.search(_competency()),
        service.search(_competency().model_copy(update={"roadmapSkillKey": "aws"})),
    )

    assert tracker["maximum"] == 2


@pytest.mark.asyncio
async def test_parallel_provider_results_keep_provider_order() -> None:
    tracker = {"active": 0, "maximum": 0}
    providers = (
        TrackingProvider("first", 0.03, tracker),
        TrackingProvider("second", 0.01, tracker),
        TrackingProvider("third", 0, tracker),
    )
    service = LearningResourceSearchService(
        providers,
        enabled=True,
        max_concurrency=3,
    )

    resources = await service.search(_competency())

    assert [resource.provider for resource in resources] == [
        "first",
        "second",
        "third",
    ]


def test_web_description_removes_markup_and_is_bounded() -> None:
    raw = """### 좋았던 점
```shell
docker run example
```
실습 중심으로 Docker 네트워크와 볼륨을 학습합니다. """ + ("반복 설명 " * 100)

    description = _summarize(raw)

    assert "###" not in description
    assert "```" not in description
    assert "docker run" not in description
    assert len(description) <= 300


def test_official_provider_is_detected_from_domain() -> None:
    assert _official_provider("https://aws.amazon.com/ko/blogs/korea/example") == "AWS"
    assert _official_provider("https://docs.docker.com/build/") == "Docker"
    assert _official_provider("https://example.com/docker") is None


@pytest.mark.asyncio
async def test_empty_book_description_gets_short_fallback() -> None:
    def handle_request(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, request=request, json={
            "documents": [{
                "title": "Docker 입문",
                "contents": "",
                "url": "https://example.com/book",
                "isbn": "5678",
                "authors": ["홍길동"],
                "publisher": "테스트 출판사",
                "thumbnail": "",
            }]
        })

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle_request)) as client:
        resource = (await LearningResourceSearchService(
            create_resource_providers(client, settings), enabled=True
        ).search(_competency()))[0]

    assert resource.description == "Docker 입문 도서로 Docker 학습에 활용할 수 있습니다."

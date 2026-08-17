import asyncio
import logging
from types import SimpleNamespace

import httpx
import pytest

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resources.provider import (
    KeenableInflearnProvider,
    KeenableWebProvider,
    create_resource_providers,
)
from api.features.roadmap.resources.search import (
    LearningResourceSearchService,
    _summarize,
)
from api.features.roadmap.schema import Competency, LearningResource


class TrackingProvider:
    def __init__(self, name: str, delay: float, tracker: dict[str, int]) -> None:
        self.name = name
        self._delay = delay
        self._tracker = tracker

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]:
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
        KEENABLE_SEARCH_ENABLED=False,
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
async def test_kakao_book_falls_back_to_competency_and_caches_result() -> None:
    queries = []

    def handle_request(request: httpx.Request) -> httpx.Response:
        query = request.url.params["query"]
        queries.append(query)
        documents = [] if query != "Docker 학습" else [{
            "title": "Docker 입문",
            "contents": "컨테이너 기초",
            "url": "https://example.com/docker-book",
            "isbn": "docker-1",
            "authors": [],
            "publisher": "테스트 출판사",
            "thumbnail": "",
        }]
        return httpx.Response(200, request=request, json={"documents": documents})

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        KEENABLE_SEARCH_ENABLED=False,
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle_request)) as client:
        service = LearningResourceSearchService(
            create_resource_providers(client, settings), enabled=True
        )
        first = await service.search(
            _competency(),
            provider_queries={"kakao_book": "Docker 네트워크 실습"},
        )
        second = await service.search(
            _competency(),
            provider_queries={"kakao_book": "Docker 네트워크 실습"},
        )

    assert [item.title for item in first] == ["Docker 입문"]
    assert [item.title for item in second] == ["Docker 입문"]
    assert queries == ["Docker 네트워크 실습", "Docker 학습"]


@pytest.mark.asyncio
async def test_keenable_web_response_is_normalized_and_cached() -> None:
    class FakeMcpClient:
        def __init__(self):
            self.calls = []

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def call_tool(self, name, arguments):
            self.calls.append((name, arguments))
            return SimpleNamespace(content=[SimpleNamespace(text=(
                "Title: Docker networking guide\n"
                "URL: https://example.com/docker-network\n"
                "Snippets:\nA practical Docker network tutorial"
            ))])

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY=None,
        KMOOC_SERVICE_KEY=None,
        KEENABLE_SEARCH_ENABLED=True,
        KEENABLE_REQUESTS_PER_SECOND=10,
    )
    fake_mcp = FakeMcpClient()
    async with httpx.AsyncClient() as client:
        service = LearningResourceSearchService((
            KeenableWebProvider(client, settings, fake_mcp),
        ), enabled=True)
        first = await service.search(_competency(), "Docker network tutorial")
        second = await service.search(_competency(), "Docker network tutorial")

    assert fake_mcp.calls == [(
        "search_web_pages", {"query": "Docker network tutorial"}
    )]
    assert [item.title for item in first] == ["Docker networking guide"]
    assert [item.title for item in second] == ["Docker networking guide"]
    assert first[0].provider == "Keenable Web Search"
    assert first[0].description == "A practical Docker network tutorial"


@pytest.mark.asyncio
async def test_keenable_inflearn_search_keeps_only_inflearn_results() -> None:
    class FakeMcpClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def call_tool(self, name, arguments):
            assert name == "search_web_pages"
            assert arguments["query"].startswith("Docker")
            assert arguments["site"] == "inflearn.com"
            return SimpleNamespace(content=[SimpleNamespace(text=(
                "Title: Docker 실전 강의\n"
                "URL: https://www.inflearn.com/course/docker-practical\n"
                "Snippets:\nDocker 컨테이너 실습 강의\n"
                "---\n"
                "Title: 외부 Docker 자료\n"
                "URL: https://example.com/docker\n"
                "Snippets:\n외부 자료"
            ))])

    settings = RoadmapSettings(
        KEENABLE_SEARCH_ENABLED=True,
        KEENABLE_REQUESTS_PER_SECOND=10,
    )
    async with httpx.AsyncClient() as client:
        resources = await KeenableInflearnProvider(
            client, settings, FakeMcpClient()
        ).search(
            _competency(),
            "Docker 컨테이너 한국어 인프런 강의",
        )

    assert len(resources) == 1
    assert resources[0].provider == "인프런"
    assert resources[0].url == "https://www.inflearn.com/ko/course/docker-practical"


@pytest.mark.asyncio
async def test_keenable_inflearn_search_keeps_only_course_landing_pages() -> None:
    class FakeMcpClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def call_tool(self, name, arguments):
            return SimpleNamespace(content=[SimpleNamespace(text=(
                "Title: 인프런 - 라이프타임 커리어 플랫폼\n"
                "URL: https://www.inflearn.com/ko/courses/it/network?skill=docker\n"
                "Snippets:\nDocker 강의 목록\n"
                "---\n"
                "Title: Docker 강의 | 질문 & 답변 - 인프런\n"
                "URL: https://www.inflearn.com/course/docker/community?cid=1\n"
                "Snippets:\nDocker 질문\n"
                "---\n"
                "Title: Docker for DevOps | Inflearn\n"
                "URL: https://www.inflearn.com/en/course/docker-devops\n"
                "Snippets:\nEnglish Docker course\n"
                "---\n"
                "Title: Docker Mastery | Inflearn\n"
                "URL: https://www.inflearn.com/course/docker-mastery\n"
                "Snippets:\nEnglish Docker course\n"
                "---\n"
                "Title: Docker 실전 강의 | 인프런\n"
                "URL: https://www.inflearn.com/ko/course/docker-practical?cid=2\n"
                "Snippets:\nDocker 컨테이너 실습"
            ))])

    settings = RoadmapSettings(
        KEENABLE_SEARCH_ENABLED=True,
        KEENABLE_REQUESTS_PER_SECOND=10,
    )
    async with httpx.AsyncClient() as client:
        resources = await KeenableInflearnProvider(
            client, settings, FakeMcpClient()
        ).search(_competency(), "Docker 인프런 강의")

    assert [resource.title for resource in resources] == [
        "Docker for DevOps | Inflearn",
        "Docker Mastery | Inflearn",
        "Docker 실전 강의 | 인프런",
    ]
    assert [resource.url for resource in resources] == [
        "https://www.inflearn.com/ko/course/docker-devops",
        "https://www.inflearn.com/ko/course/docker-mastery",
        "https://www.inflearn.com/ko/course/docker-practical?cid=2",
    ]


@pytest.mark.asyncio
async def test_keenable_retries_rate_limit_response() -> None:
    class FlakyMcpClient:
        def __init__(self):
            self.calls = 0

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def call_tool(self, name, arguments):
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("rate limited")
            return SimpleNamespace(content=[])

    settings = RoadmapSettings(
        KAKAO_REST_API_KEY=None,
        KMOOC_SERVICE_KEY=None,
        KEENABLE_SEARCH_ENABLED=True,
        KEENABLE_REQUESTS_PER_SECOND=10,
        KEENABLE_MAX_RETRIES=1,
    )
    fake_mcp = FlakyMcpClient()
    async with httpx.AsyncClient() as client:
        result = await LearningResourceSearchService((
            KeenableWebProvider(client, settings, fake_mcp),
        ), enabled=True).search(_competency(), "Docker")

    assert result == []
    assert fake_mcp.calls == 2


@pytest.mark.asyncio
async def test_provider_failure_does_not_fail_search(caplog) -> None:
    def failed_get(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("unavailable")

    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        KEENABLE_SEARCH_ENABLED=False,
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
    assert empty_providers == {"kmooc", "keenable", "keenable_inflearn"}


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
        KEENABLE_SEARCH_ENABLED=False,
    )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle_request)) as client:
        resource = (await LearningResourceSearchService(
            create_resource_providers(client, settings), enabled=True
        ).search(_competency()))[0]

    assert resource.description == "Docker 입문 도서로 Docker 학습에 활용할 수 있습니다."

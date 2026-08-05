import httpx

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resource_search import (
    LearningResourceSearchService,
    _official_provider,
    _summarize,
)
from api.features.roadmap.schema import Competency


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


def test_kakao_book_response_is_normalized(monkeypatch) -> None:
    def fake_get(url, **kwargs):
        assert url == "https://dapi.kakao.com/v3/search/book"
        return httpx.Response(200, request=httpx.Request("GET", url), json={
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

    monkeypatch.setattr(httpx, "get", fake_get)
    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    resources = LearningResourceSearchService(settings).search(_competency())

    assert len(resources) == 1
    assert resources[0].resourceType == "BOOK"
    assert resources[0].title == "Docker 실전 가이드"
    assert resources[0].authors == ["홍길동"]


def test_provider_failure_does_not_fail_search(monkeypatch) -> None:
    def failed_get(*args, **kwargs):
        raise httpx.ConnectError("unavailable")

    monkeypatch.setattr(httpx, "get", failed_get)
    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    assert LearningResourceSearchService(settings).search(_competency()) == []


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


def test_empty_book_description_gets_short_fallback(monkeypatch) -> None:
    def fake_get(url, **kwargs):
        return httpx.Response(200, request=httpx.Request("GET", url), json={
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

    monkeypatch.setattr(httpx, "get", fake_get)
    settings = RoadmapSettings(
        ROADMAP_RESOURCE_SEARCH_ENABLED=True,
        KAKAO_REST_API_KEY="key",
        KMOOC_SERVICE_KEY=None,
        TAVILY_API_KEY=None,
    )

    resource = LearningResourceSearchService(settings).search(_competency())[0]

    assert resource.description == "Docker 입문 도서로 Docker 학습에 활용할 수 있습니다."

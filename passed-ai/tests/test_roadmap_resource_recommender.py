import pytest

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resource_recommender import (
    LearningResourceRecommender,
    RecommendationTarget,
    build_book_search_query,
    build_milestone_search_query,
    build_web_search_query,
)
from api.features.roadmap.schema import LearningResource


def _resource(resource_id: str, url: str | None = None) -> LearningResource:
    return LearningResource(
        resourceId=resource_id,
        resourceType="WEB_RESOURCE",
        title=f"자료 {resource_id}",
        description="검색 결과 설명",
        provider="Test",
        url=url or f"https://example.com/{resource_id}",
    )


def _book(resource_id: str, title: str) -> LearningResource:
    return LearningResource(
        resourceId=resource_id,
        resourceType="BOOK",
        title=title,
        provider="Test",
        url=f"https://example.com/books/{resource_id}",
    )


def _target(candidates: list[LearningResource] | None = None) -> RecommendationTarget:
    return RecommendationTarget(
        key="javascript",
        competency_name="JavaScript",
        competency_context="웹 애플리케이션 프론트엔드 개발",
        title="비동기 오류 처리",
        learning_objective="Promise 오류 전파와 재시도를 구현한다.",
        completion_criteria="오류 처리 테스트를 통과한다.",
        candidates=candidates or [],
    )


def test_milestone_query_contains_job_and_completion_context() -> None:
    query = build_milestone_search_query(_target())

    assert "JavaScript" in query
    assert "웹 애플리케이션" in query
    assert "비동기 오류 처리" in query
    assert "Promise 오류 전파" in query
    assert "오류 처리 테스트" in query
    assert len(query) <= 500


def test_book_query_contains_competency_milestone_and_job_context() -> None:
    query = build_book_search_query(_target())

    assert query.startswith("JavaScript 비동기 오류 처리")
    assert "웹 애플리케이션" in query
    assert len(query) <= 80


def test_web_query_focuses_on_milestone_without_broad_context() -> None:
    query = build_web_search_query(_target())

    assert query.startswith("JavaScript 비동기 오류 처리")
    assert "Promise 오류 전파" in query
    assert "tutorial guide" in query
    assert "웹 애플리케이션" not in query
    assert "오류 처리 테스트" not in query
    assert len(query) <= 180


@pytest.mark.asyncio
async def test_searches_every_milestone_and_keeps_top_three() -> None:
    targets = [_target(), _target()]
    targets[1] = RecommendationTarget(**{
        **targets[1].__dict__, "key": "typescript"
    })
    calls = []

    async def search(target):
        calls.append(target.key)
        return [_resource(str(index)) for index in range(4)]

    result = await LearningResourceRecommender(RoadmapSettings()).recommend(
        targets, additional_search=search
    )

    assert calls == ["javascript", "typescript"]
    assert [item.resourceId for item in result["javascript"]] == ["0", "1", "2"]
    assert [item.resourceId for item in result["typescript"]] == ["0", "1", "2"]


@pytest.mark.asyncio
async def test_removes_duplicate_and_invalid_urls_without_semantic_filtering() -> None:
    target = _target([
        _resource("first", "https://example.com/course"),
        _resource("duplicate-url", "https://example.com/course/"),
        _resource("invalid", "javascript:alert(1)"),
        _resource("last", "https://example.com/last"),
    ])

    result = await LearningResourceRecommender(RoadmapSettings()).recommend([target])

    assert [item.resourceId for item in result[target.key]] == ["first", "last"]
    assert all("비동기 오류 처리" in item.description for item in result[target.key])


@pytest.mark.asyncio
async def test_books_keep_search_order_without_semantic_filtering() -> None:
    target = _target([
        _book("first", "모던 JavaScript 입문"),
        _book("unrelated", "이더리움 블록체인 프로젝트"),
        _book("second", "JavaScript 웹 프로그래밍"),
        _book("third", "JavaScript 완벽 가이드"),
    ])

    result = await LearningResourceRecommender(RoadmapSettings()).recommend([target])

    assert [item.resourceId for item in result[target.key]] == [
        "first", "unrelated"
    ]

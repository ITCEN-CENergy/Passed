import pytest

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resource_recommender import (
    LearningResourceRecommender,
    RecommendationTarget,
    build_book_search_query,
    build_milestone_search_query,
    build_web_search_query,
    classify_resource_relevance,
)
from api.features.roadmap.schema import LearningResource


def _resource(resource_id: str, url: str | None = None) -> LearningResource:
    return LearningResource(
        resourceId=resource_id,
        resourceType="WEB_RESOURCE",
        title=f"JavaScript 비동기 오류 처리 자료 {resource_id}",
        description="Promise 오류 전파와 재시도를 다루는 검색 결과 설명",
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

    assert query.startswith("JavaScript 웹 애플리케이션 프론트엔드 개발 비동기 오류 처리")
    assert "웹 애플리케이션" in query
    assert len(query) <= 80


def test_web_query_focuses_on_milestone_without_broad_context() -> None:
    query = build_web_search_query(_target())

    assert query.startswith('"JavaScript" 웹 애플리케이션 프론트엔드 개발 비동기 오류 처리')
    assert "Promise 오류 전파" in query
    assert "tutorial guide" in query
    assert "웹 애플리케이션" in query
    assert "오류 처리 테스트" not in query
    assert len(query) <= 240


def test_query_uses_dynamic_standard_competency_description() -> None:
    target = RecommendationTarget(
        key="ambiguous",
        competency_name="기술 발표",
        competency_context=(
            "기술의 핵심 메시지와 근거를 청중의 수준과 시간에 맞춰 "
            "자료와 말로 전달하는 실무 역량"
        ),
        title="기술 발표 기초 학습",
        learning_objective="기술 발표의 기본 원리를 적용한다.",
        completion_criteria="검증 가능한 결과물을 제출한다.",
        candidates=[],
    )

    query = build_web_search_query(target)

    assert query.startswith('"기술 발표"')
    assert "핵심 메시지" in query
    assert "청중의 수준" in query


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
async def test_removes_duplicate_and_invalid_urls_after_relevance_filtering() -> None:
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
async def test_books_exclude_irrelevant_results_and_keep_two_relevant_books() -> None:
    target = _target([
        _book("first", "모던 JavaScript 입문"),
        _book("unrelated", "이더리움 블록체인 프로젝트"),
        _book("second", "JavaScript 웹 프로그래밍"),
        _book("third", "JavaScript 완벽 가이드"),
    ])

    result = await LearningResourceRecommender(RoadmapSettings()).recommend([target])

    assert [item.resourceId for item in result[target.key]] == ["first", "second"]


@pytest.mark.asyncio
async def test_books_with_normalized_duplicate_titles_are_kept_once() -> None:
    target = _target([
        _book("first", "코딩 자율학습 JavaScript 입문"),
        _book("duplicate", "코딩 자율학습: JAVASCRIPT 입문!"),
        _book("second", "모던 JavaScript 완벽 가이드"),
    ])

    result = await LearningResourceRecommender(RoadmapSettings()).recommend([target])

    assert [item.resourceId for item in result[target.key]] == ["first", "second"]


def test_relevance_distinguishes_milestone_competency_and_irrelevant() -> None:
    target = _target()

    direct = _resource("direct")
    competency_only = _book("competency", "모던 JavaScript 입문")
    irrelevant = _book("irrelevant", "이더리움 블록체인 프로젝트")

    assert classify_resource_relevance(target, direct) == 2
    assert classify_resource_relevance(target, competency_only) == 1
    assert classify_resource_relevance(target, irrelevant) == 0


def test_multiword_competency_does_not_match_one_generic_name_token() -> None:
    target = RecommendationTarget(
        **{
            **_target().__dict__,
            "competency_name": "상용 서비스 운영",
            "distinctive_terms": ("가용성", "장애", "모니터링"),
        }
    )
    generic = LearningResource(
        resourceId="generic",
        resourceType="WEB_RESOURCE",
        title="게임 서비스 사용 가이드",
        description="초보자를 위한 튜토리얼",
        provider="Test",
        url="https://example.com/generic",
    )

    assert classify_resource_relevance(target, generic) == 0


def test_curated_excluded_term_rejects_otherwise_matching_resource() -> None:
    target = RecommendationTarget(
        **{
            **_target().__dict__,
            "excluded_terms": ("davinci resolve",),
        }
    )
    resource = LearningResource(
        resourceId="noise",
        resourceType="WEB_RESOURCE",
        title="DaVinci Resolve JavaScript Tutorial",
        description="JavaScript 비동기 오류 처리 가이드",
        provider="Test",
        url="https://example.com/noise",
    )

    assert classify_resource_relevance(target, resource) == 0


@pytest.mark.asyncio
async def test_cross_competency_repeated_url_is_removed_as_search_noise() -> None:
    shared = LearningResource(
        resourceId="shared",
        resourceType="WEB_RESOURCE",
        title="Universal Workflow Course",
        description="universal workflow 실습 course",
        provider="Test",
        url="https://example.com/shared",
    )
    targets = [
        RecommendationTarget(
            **{
                **_target([shared]).__dict__,
                "key": f"competency-{index}",
                "competency_name": f"역량 {index}",
                "distinctive_terms": ("universal", "workflow"),
            }
        )
        for index in range(3)
    ]

    result = await LearningResourceRecommender(RoadmapSettings()).recommend(targets)

    assert all(not resources for resources in result.values())

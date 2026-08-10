from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import urlparse

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.schema import LearningResource


@dataclass(frozen=True)
class RecommendationTarget:
    key: str
    competency_name: str
    competency_context: str
    title: str
    learning_objective: str
    completion_criteria: str
    candidates: list[LearningResource]


ResourceSearch = Callable[
    [RecommendationTarget], Awaitable[list[LearningResource]]
]


def build_milestone_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        target.competency_name,
        target.competency_context[:180],
        target.title,
        target.learning_objective,
        target.completion_criteria,
        "학습 자료 실습 튜토리얼 강의 공식 문서",
    )))[:500]


def build_book_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        target.competency_name,
        target.title,
        target.competency_context,
    )))[:80]


def build_web_search_query(target: RecommendationTarget) -> str:
    """Keep web search focused on the concrete milestone learning task."""
    return " ".join(filter(None, (
        target.competency_name,
        target.title,
        target.learning_objective,
        "tutorial guide",
    )))[:180]


def _is_http_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def _recommendation_reason(target: RecommendationTarget) -> str:
    return (
        f"이 자료는 '{target.title}'의 학습 목표를 수행하고 "
        "완료 기준을 점검하는 데 활용할 수 있습니다."
    )


class LearningResourceRecommender:
    def __init__(self, settings: RoadmapSettings) -> None:
        self._settings = settings

    async def recommend(
        self,
        targets: list[RecommendationTarget],
        additional_search: ResourceSearch | None = None,
    ) -> dict[str, list[LearningResource]]:
        if additional_search is None:
            search_results = [target.candidates for target in targets]
        else:
            search_results = await asyncio.gather(*(
                additional_search(target) for target in targets
            ))

        recommendations: dict[str, list[LearningResource]] = {}
        for target, resources in zip(targets, search_results, strict=True):
            unique: dict[str, LearningResource] = {}
            seen_urls: set[str] = set()
            book_count = 0
            for resource in resources:
                if not _is_http_url(resource.url):
                    continue
                if resource.resourceType.value == "BOOK":
                    if book_count >= 2:
                        continue
                normalized_url = resource.url.rstrip("/")
                if resource.resourceId in unique or normalized_url in seen_urls:
                    continue
                seen_urls.add(normalized_url)
                unique[resource.resourceId] = resource.model_copy(update={
                    "description": _recommendation_reason(target)
                })
                if resource.resourceType.value == "BOOK":
                    book_count += 1
                if len(unique) >= self._settings.resource_recommendation_limit:
                    break
            recommendations[target.key] = list(unique.values())
        return recommendations

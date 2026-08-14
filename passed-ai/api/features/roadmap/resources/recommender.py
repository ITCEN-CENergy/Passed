from __future__ import annotations

import asyncio
import re
import unicodedata
from collections.abc import Awaitable, Callable
from collections import defaultdict
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
    distinctive_terms: tuple[str, ...] = ()
    excluded_terms: tuple[str, ...] = ()


ResourceSearch = Callable[
    [RecommendationTarget], Awaitable[list[LearningResource]]
]

_MAX_URL_USES_PER_COMPETENCY = 2


def _focus_terms(target: RecommendationTarget) -> str:
    return target.competency_context.strip()


def build_milestone_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        f'"{target.competency_name}"',
        _focus_terms(target),
        target.title,
        target.learning_objective,
        target.completion_criteria,
        "학습 자료 실습 튜토리얼 강의 공식 문서",
    )))[:500]


def build_book_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        target.competency_name,
        _focus_terms(target),
        target.title,
    )))[:80]


def build_web_search_query(target: RecommendationTarget) -> str:
    """Keep web search focused on the concrete milestone learning task."""
    return " ".join(filter(None, (
        f'"{target.competency_name}"',
        _focus_terms(target),
        target.title,
        target.learning_objective,
        "공식 문서 tutorial guide",
    )))[:240]


def build_inflearn_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        target.competency_name,
        target.title,
        target.learning_objective,
        "한국어 한글 인프런 실습 강의",
    )))[:240]


def build_competency_search_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        f'"{target.competency_name}"',
        _focus_terms(target),
        "입문 실습 학습 가이드 강의 공식 문서 tutorial",
    )))[:300]


def build_competency_book_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        target.competency_name,
        _focus_terms(target),
        "입문 실무",
    )))[:80]


def build_competency_web_query(target: RecommendationTarget) -> str:
    return " ".join(filter(None, (
        f'"{target.competency_name}"',
        _focus_terms(target),
        "공식 문서 tutorial course guide",
    )))[:240]


_STOP_WORDS = {
    "가이드", "강의", "개발", "공식", "기본", "기초", "도구", "문서", "목표", "사용",
    "사용할", "실무", "실습", "역량", "완료", "이해", "입문", "자료", "적용",
    "학습", "guide", "introduction", "learn", "learning", "official", "tutorial",
}


def _tokens(value: str) -> set[str]:
    return {
        token for token in re.findall(r"[가-힣A-Za-z0-9+#.]+", value.casefold())
        if len(token) >= 2 and token not in _STOP_WORDS
    }


def _matches_distinctive_term(
    term: str,
    resource_text: str,
    resource_tokens: set[str],
) -> bool:
    normalized_term = term.casefold().strip()
    if not normalized_term:
        return False
    if normalized_term in resource_text:
        return True
    term_tokens = _tokens(normalized_term)
    return bool(term_tokens) and term_tokens <= resource_tokens


def classify_resource_relevance(
    target: RecommendationTarget,
    resource: LearningResource,
) -> int:
    """2=마일스톤 직접 관련, 1=역량 관련, 0=무관."""
    resource_text = f"{resource.title} {resource.description}".casefold()
    if any(term in resource_text for term in target.excluded_terms):
        return 0
    competency_phrase = target.competency_name.casefold().strip()
    resource_tokens = _tokens(resource_text)
    name_tokens = _tokens(target.competency_name)
    matched_distinctive_terms = sum(
        _matches_distinctive_term(term, resource_text, resource_tokens)
        for term in target.distinctive_terms
    )
    distinctive_tokens = {
        token
        for term in target.distinctive_terms
        for token in _tokens(term)
    }
    matched_name_tokens = name_tokens & resource_tokens
    required_name_matches = 1 if len(name_tokens) <= 1 else 2
    competency_related = (
        bool(competency_phrase and competency_phrase in resource_text)
        or len(matched_name_tokens) >= required_name_matches
        or matched_distinctive_terms >= 2
    )
    if not competency_related:
        return 0
    milestone_tokens = _tokens(
        f"{target.title} {target.learning_objective} {target.completion_criteria}"
    ) - name_tokens - distinctive_tokens
    return 2 if milestone_tokens & resource_tokens else 1


def _is_http_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def _normalized_book_title(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return "".join(re.findall(r"[가-힣a-z0-9]+", normalized))


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

        url_competencies: defaultdict[str, set[str]] = defaultdict(set)
        for target, resources in zip(targets, search_results, strict=True):
            competency_key = target.key.rsplit(":", 3)[0]
            for resource in resources:
                if _is_http_url(resource.url):
                    url_competencies[resource.url.rstrip("/").casefold()].add(
                        competency_key
                    )

        recommendations: dict[str, list[LearningResource]] = {}
        url_usage_by_competency: defaultdict[str, dict[str, int]] = defaultdict(dict)
        for target, resources in zip(targets, search_results, strict=True):
            competency_key = target.key.rsplit(":", 3)[0]
            competency_url_usage = url_usage_by_competency[competency_key]
            unique: dict[str, LearningResource] = {}
            seen_urls: set[str] = set()
            seen_book_titles: set[str] = set()
            book_count = 0
            inflearn_count = 0
            ranked_resources = sorted(
                resources,
                key=lambda resource: (
                    classify_resource_relevance(target, resource),
                    resource.provider == "인프런",
                    -competency_url_usage.get(
                        resource.url.rstrip("/").casefold(), 0
                    ),
                ),
                reverse=True,
            )
            for resource in ranked_resources:
                if classify_resource_relevance(target, resource) == 0:
                    continue
                if not _is_http_url(resource.url):
                    continue
                normalized_url = resource.url.rstrip("/").casefold()
                if (
                    competency_url_usage.get(normalized_url, 0)
                    >= _MAX_URL_USES_PER_COMPETENCY
                    and unique
                ):
                    continue
                resource_text = f"{resource.title} {resource.description}".casefold()
                if (
                    len(url_competencies[normalized_url]) >= 3
                    and target.competency_name.casefold() not in resource_text
                ):
                    continue
                if resource.resourceType.value == "BOOK":
                    if book_count >= 2:
                        continue
                    normalized_title = _normalized_book_title(resource.title)
                    if normalized_title in seen_book_titles:
                        continue
                if resource.provider == "인프런" and inflearn_count >= 2:
                    continue
                if resource.resourceId in unique or normalized_url in seen_urls:
                    continue
                seen_urls.add(normalized_url)
                competency_url_usage[normalized_url] = (
                    competency_url_usage.get(normalized_url, 0) + 1
                )
                unique[resource.resourceId] = resource.model_copy(update={
                    "description": _recommendation_reason(target)
                })
                if resource.resourceType.value == "BOOK":
                    seen_book_titles.add(normalized_title)
                    book_count += 1
                if resource.provider == "인프런":
                    inflearn_count += 1
                if len(unique) >= self._settings.resource_recommendation_limit:
                    break
            recommendations[target.key] = list(unique.values())
        return recommendations

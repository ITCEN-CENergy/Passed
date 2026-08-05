import hashlib
import html
import re
from urllib.parse import unquote, urlparse
from typing import Any

import httpx

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.schema import (
    Competency,
    LearningResource,
    LearningResourceType,
)


def _resource_id(provider: str, external_id: str) -> str:
    digest = hashlib.sha256(external_id.encode("utf-8")).hexdigest()[:16]
    return f"{provider.lower()}-{digest}"


def _clean(value: Any) -> str:
    text = html.unescape(str(value or ""))
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _summarize(value: Any, maximum_length: int = 300) -> str:
    text = html.unescape(str(value or ""))
    text = re.sub(r"```[\s\S]*?```", " ", text)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"!?(?:\[([^]]*)])\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\.{3}|\[…]|\[\.\.\.]", " ", text)
    text = re.sub(r"(?:^|\s)#{1,6}\s*", " ", text)
    text = re.sub(r"(?:^|\s)[>*+-]\s+", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) <= maximum_length:
        return text
    candidate = text[: maximum_length + 1]
    sentence_end = max(candidate.rfind(". "), candidate.rfind("다. "), candidate.rfind("요. "))
    if sentence_end >= maximum_length // 2:
        return candidate[: sentence_end + 1].strip()
    return text[: maximum_length - 1].rstrip(" ,.;:") + "…"


_OFFICIAL_DOMAINS = {
    "aws.amazon.com": "AWS",
    "docs.aws.amazon.com": "AWS",
    "docs.docker.com": "Docker",
    "docker.com": "Docker",
    "kubernetes.io": "Kubernetes",
    "learn.microsoft.com": "Microsoft Learn",
    "docs.oracle.com": "Oracle",
    "docs.spring.io": "Spring",
    "python.org": "Python",
}


def _official_provider(url: str) -> str | None:
    hostname = (urlparse(url).hostname or "").lower()
    for domain, provider in _OFFICIAL_DOMAINS.items():
        if hostname == domain or hostname.endswith(f".{domain}"):
            return provider
    return None


class LearningResourceSearchService:
    """Best-effort aggregation. One broken provider never fails roadmap generation."""

    def __init__(self, settings: RoadmapSettings) -> None:
        self._settings = settings

    def search(self, competency: Competency) -> list[LearningResource]:
        if not self._settings.resource_search_enabled:
            return []
        resources: list[LearningResource] = []
        for provider in (self._search_kmooc, self._search_kakao, self._search_web):
            try:
                resources.extend(provider(competency))
            except Exception:
                # Search is supplemental. The LLM can still create a roadmap when a
                # provider is unavailable or its response has changed.
                continue
        deduplicated: dict[str, LearningResource] = {}
        for resource in resources:
            deduplicated.setdefault(resource.url, resource)
        return list(deduplicated.values())[:15]

    def _search_kmooc(self, competency: Competency) -> list[LearningResource]:
        settings = self._settings
        if not settings.kmooc_service_key or not settings.kmooc_course_list_url:
            return []
        # This API exposes pagination but no server-side keyword search. Scan a
        # bounded number of recent pages, then keep only locally relevant courses.
        items: list[dict[str, Any]] = []
        keyword = competency.standardCompetencyName.casefold()
        for page in range(1, 11):
            response = httpx.get(
                settings.kmooc_course_list_url,
                params={
                    "serviceKey": unquote(settings.kmooc_service_key),
                    "page": page,
                },
                timeout=settings.resource_search_timeout_seconds,
            )
            response.raise_for_status()
            candidates = self._find_items(response.json())
            items.extend(
                item for item in candidates
                if keyword in " ".join(
                    _clean(item.get(field)).casefold()
                    for field in ("name", "shortname", "org", "org_name", "professor")
                )
            )
            if len(items) >= 5 or not candidates:
                break
        result = []
        for item in items[:5]:
            title = _clean(item.get("courseName") or item.get("name") or item.get("title"))
            url = _clean(item.get("courseUrl") or item.get("url") or item.get("link"))
            if not title or not url:
                continue
            external_id = _clean(
                item.get("courseId") or item.get("course_id") or item.get("id") or url
            )
            result.append(LearningResource(
                resourceId=_resource_id("kmooc", external_id),
                resourceType=LearningResourceType.KMOOC_COURSE,
                title=title,
                description=_summarize(
                    item.get("courseDescription") or item.get("description")
                ),
                provider=_clean(item.get("org_name")) or "K-MOOC",
                url=url,
                thumbnailUrl=_clean(
                    item.get("thumbnail") or item.get("image") or item.get("course_image")
                ) or None,
                authors=[value for value in [
                    _clean(item.get("professor") or item.get("instructor"))
                ] if value],
                isOfficial=True,
                isFree=True,
            ))
        return result

    def _search_kakao(self, competency: Competency) -> list[LearningResource]:
        key = self._settings.kakao_rest_api_key
        if not key:
            return []
        response = httpx.get(
            "https://dapi.kakao.com/v3/search/book",
            params={"query": competency.standardCompetencyName, "size": 5, "sort": "accuracy"},
            headers={"Authorization": f"KakaoAK {key}"},
            timeout=self._settings.resource_search_timeout_seconds,
        )
        response.raise_for_status()
        result = []
        for item in response.json().get("documents", []):
            title, url = _clean(item.get("title")), _clean(item.get("url"))
            if not title or not url:
                continue
            authors = [_clean(author) for author in item.get("authors", []) if _clean(author)]
            description = _summarize(item.get("contents"))
            if not description:
                description = f"{title} 도서로 {competency.standardCompetencyName} 학습에 활용할 수 있습니다."
            result.append(LearningResource(
                resourceId=_resource_id("kakao-book", _clean(item.get("isbn")) or url),
                resourceType=LearningResourceType.BOOK,
                title=title,
                description=description,
                provider=_clean(item.get("publisher")) or "Kakao Book Search",
                url=url,
                thumbnailUrl=_clean(item.get("thumbnail")) or None,
                authors=authors,
                isOfficial=False,
                isFree=None,
            ))
        return result

    def _search_web(self, competency: Competency) -> list[LearningResource]:
        key = self._settings.tavily_api_key
        if not key:
            return []
        from tavily import TavilyClient

        response = TavilyClient(api_key=key).search(
            query=f"{competency.standardCompetencyName} 학습 가이드 공식 문서 실습",
            search_depth="advanced",
            max_results=5,
        )
        result = []
        for item in response.get("results", []):
            title, url = _clean(item.get("title")), _clean(item.get("url"))
            if not title or not url:
                continue
            official_provider = _official_provider(url)
            result.append(LearningResource(
                resourceId=_resource_id("web", url),
                resourceType=LearningResourceType.WEB_RESOURCE,
                title=title,
                description=_summarize(item.get("content")),
                provider=official_provider or "Web Search",
                url=url,
                authors=[],
                isOfficial=official_provider is not None,
                isFree=True,
            ))
        return result

    @classmethod
    def _find_items(cls, value: Any) -> list[dict[str, Any]]:
        if isinstance(value, list) and all(isinstance(item, dict) for item in value):
            return value
        if isinstance(value, dict):
            for key in ("items", "courses", "results", "data", "item"):
                if key in value:
                    found = cls._find_items(value[key])
                    if found:
                        return found
            for child in value.values():
                found = cls._find_items(child)
                if found:
                    return found
        return []

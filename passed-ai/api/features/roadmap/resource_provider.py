import asyncio
import hashlib
import html
import re
from typing import Any, Protocol
from urllib.parse import unquote

import httpx
from tavily import AsyncTavilyClient

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.schema import (
    Competency,
    LearningResource,
    LearningResourceType,
)


class LearningResourceProvider(Protocol):
    name: str

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]: ...


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


def _find_items(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list) and all(isinstance(item, dict) for item in value):
        return value
    if isinstance(value, dict):
        for key in ("items", "courses", "results", "data", "item"):
            if key in value:
                found = _find_items(value[key])
                if found:
                    return found
        for child in value.values():
            found = _find_items(child)
            if found:
                return found
    return []


class KmoocProvider:
    name = "kmooc"

    def __init__(self, client: httpx.AsyncClient, settings: RoadmapSettings) -> None:
        self._client = client
        self._settings = settings
        self._cache: dict[int, list[LearningResource]] = {}
        self._locks: dict[int, asyncio.Lock] = {}

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]:
        cached = self._cache.get(competency.standardCompetencyId)
        if cached is not None:
            return cached
        lock = self._locks.setdefault(
            competency.standardCompetencyId, asyncio.Lock()
        )
        async with lock:
            cached = self._cache.get(competency.standardCompetencyId)
            if cached is not None:
                return cached
            result = await self._search_uncached(competency)
            self._cache[competency.standardCompetencyId] = result
            return result

    async def _search_uncached(
        self, competency: Competency
    ) -> list[LearningResource]:
        settings = self._settings
        if not settings.kmooc_service_key or not settings.kmooc_course_list_url:
            return []
        items: list[dict[str, Any]] = []
        keyword = competency.standardCompetencyName.casefold()
        for page in range(1, 11):
            response = await self._client.get(
                settings.kmooc_course_list_url,
                params={
                    "serviceKey": unquote(settings.kmooc_service_key),
                    "page": page,
                },
                timeout=settings.resource_search_timeout_seconds,
            )
            response.raise_for_status()
            candidates = _find_items(response.json())
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
                isFree=True,
            ))
        return result


class KakaoBookProvider:
    name = "kakao_book"

    def __init__(self, client: httpx.AsyncClient, settings: RoadmapSettings) -> None:
        self._client = client
        self._settings = settings
        self._cache: dict[str, list[LearningResource]] = {}
        self._locks: dict[str, asyncio.Lock] = {}

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]:
        key = self._settings.kakao_rest_api_key
        if not key:
            return []
        primary_query = search_query[:80].strip()
        resources = await self._search_query(competency, primary_query, key)
        fallback_query = (
            f"{competency.standardCompetencyName} 학습"
        )[:80].strip()
        if resources or not fallback_query or fallback_query == primary_query:
            return resources
        return await self._search_query(competency, fallback_query, key)

    async def _search_query(
        self,
        competency: Competency,
        query: str,
        key: str,
    ) -> list[LearningResource]:
        cached = self._cache.get(query)
        if cached is not None:
            return cached
        lock = self._locks.setdefault(query, asyncio.Lock())
        async with lock:
            cached = self._cache.get(query)
            if cached is not None:
                return cached
            resources = await self._request(competency, query, key)
            self._cache[query] = resources
            return resources

    async def _request(
        self,
        competency: Competency,
        query: str,
        key: str,
    ) -> list[LearningResource]:
        response = await self._client.get(
            "https://dapi.kakao.com/v3/search/book",
            params={"query": query, "size": 5, "sort": "accuracy"},
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
                isFree=None,
            ))
        return result


class TavilyProvider:
    name = "tavily"

    def __init__(self, client: httpx.AsyncClient, settings: RoadmapSettings) -> None:
        self._settings = settings
        self._client = (
            AsyncTavilyClient(api_key=settings.tavily_api_key, client=client)
            if settings.tavily_api_key else None
        )

    async def search(
        self, competency: Competency, search_query: str
    ) -> list[LearningResource]:
        if self._client is None:
            return []
        response = await self._client.search(
            query=search_query,
            search_depth="advanced",
            max_results=5,
            timeout=self._settings.resource_search_timeout_seconds,
        )
        result = []
        for item in response.get("results", []):
            title, url = _clean(item.get("title")), _clean(item.get("url"))
            if not title or not url:
                continue
            result.append(LearningResource(
                resourceId=_resource_id("web", url),
                resourceType=LearningResourceType.WEB_RESOURCE,
                title=title,
                description=_summarize(item.get("content")),
                provider="Web Search",
                url=url,
                authors=[],
                isFree=True,
            ))
        return result


def create_resource_providers(
    client: httpx.AsyncClient,
    settings: RoadmapSettings,
) -> tuple[LearningResourceProvider, ...]:
    return (
        KmoocProvider(client, settings),
        KakaoBookProvider(client, settings),
        TavilyProvider(client, settings),
    )

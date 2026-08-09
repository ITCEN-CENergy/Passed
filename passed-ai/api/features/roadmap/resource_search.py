import asyncio
import logging
from time import perf_counter

from api.features.roadmap.resource_provider import (
    LearningResourceProvider,
    _summarize,
)
from api.features.roadmap.schema import Competency, LearningResource


logger = logging.getLogger(__name__)


class LearningResourceSearchService:
    """Sequential best-effort aggregation over asynchronous providers."""

    def __init__(
        self,
        providers: tuple[LearningResourceProvider, ...],
        enabled: bool,
        max_concurrency: int = 6,
        generation_id: str | None = None,
    ) -> None:
        self._providers = providers
        self._enabled = enabled
        self._semaphore = asyncio.Semaphore(max_concurrency)
        self._generation_id = generation_id

    async def search(
        self, competency: Competency, search_query: str | None = None
    ) -> list[LearningResource]:
        if not self._enabled:
            logger.info(
                "roadmap_resource_search_skipped generationId=%s competencyKey=%s reason=disabled",
                self._generation_id,
                competency.roadmapSkillKey,
                extra={
                    "event": "roadmap_resource_search_skipped",
                    "generationId": self._generation_id,
                    "competencyKey": competency.roadmapSkillKey,
                    "reason": "disabled",
                },
            )
            return []

        search_started = perf_counter()
        query = search_query or (
            f"{competency.standardCompetencyName} "
            f"{competency.category.value.replace('_', ' ')} 학습 가이드 실무 실습"
        )
        provider_results = await asyncio.gather(*(
            self._search_provider(provider, competency, query)
            for provider in self._providers
        ))
        resources = [
            resource
            for provider_resources in provider_results
            for resource in provider_resources
        ]

        deduplicated: dict[str, LearningResource] = {}
        for resource in resources:
            deduplicated.setdefault(resource.url, resource)
        result = list(deduplicated.values())[:15]
        elapsed_ms = round((perf_counter() - search_started) * 1000)
        logger.info(
            "roadmap_competency_search_completed generationId=%s competencyKey=%s "
            "resultCount=%d elapsedMs=%d",
            self._generation_id,
            competency.roadmapSkillKey,
            len(result),
            elapsed_ms,
            extra={
                "event": "roadmap_competency_search_completed",
                "generationId": self._generation_id,
                "competencyKey": competency.roadmapSkillKey,
                "resultCount": len(result),
                "elapsedMs": elapsed_ms,
            },
        )
        return result

    async def _search_provider(
        self,
        provider: LearningResourceProvider,
        competency: Competency,
        search_query: str,
    ) -> list[LearningResource]:
        queued_at = perf_counter()
        wait_ms = 0
        try:
            async with self._semaphore:
                provider_started = perf_counter()
                wait_ms = round((provider_started - queued_at) * 1000)
                provider_resources = await provider.search(competency, search_query)
            status = "SUCCESS" if provider_resources else "EMPTY"
            elapsed_ms = round((perf_counter() - provider_started) * 1000)
            logger.info(
                "roadmap_provider_search_completed generationId=%s competencyKey=%s "
                "provider=%s status=%s resultCount=%d waitMs=%d elapsedMs=%d",
                self._generation_id,
                competency.roadmapSkillKey,
                provider.name,
                status,
                len(provider_resources),
                wait_ms,
                elapsed_ms,
                extra={
                    "event": "roadmap_provider_search_completed",
                    "generationId": self._generation_id,
                    "competencyKey": competency.roadmapSkillKey,
                    "provider": provider.name,
                    "status": status,
                    "resultCount": len(provider_resources),
                    "waitMs": wait_ms,
                    "elapsedMs": elapsed_ms,
                },
            )
            return provider_resources
        except Exception as exception:
            elapsed_ms = round((perf_counter() - provider_started) * 1000)
            logger.warning(
                "roadmap_provider_search_completed generationId=%s competencyKey=%s "
                "provider=%s status=FAILED resultCount=0 waitMs=%d elapsedMs=%d errorType=%s",
                self._generation_id,
                competency.roadmapSkillKey,
                provider.name,
                wait_ms,
                elapsed_ms,
                type(exception).__name__,
                extra={
                    "event": "roadmap_provider_search_completed",
                    "generationId": self._generation_id,
                    "competencyKey": competency.roadmapSkillKey,
                    "provider": provider.name,
                    "status": "FAILED",
                    "resultCount": 0,
                    "waitMs": wait_ms,
                    "elapsedMs": elapsed_ms,
                    "errorType": type(exception).__name__,
                },
            )
            return []


__all__ = [
    "LearningResourceSearchService",
    "_summarize",
]

import logging
from time import perf_counter

from api.features.roadmap.resource_provider import (
    LearningResourceProvider,
    _official_provider,
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
        generation_id: str | None = None,
    ) -> None:
        self._providers = providers
        self._enabled = enabled
        self._generation_id = generation_id

    async def search(self, competency: Competency) -> list[LearningResource]:
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
        resources: list[LearningResource] = []
        for provider in self._providers:
            provider_started = perf_counter()
            try:
                provider_resources = await provider.search(competency)
                resources.extend(provider_resources)
                status = "SUCCESS" if provider_resources else "EMPTY"
                elapsed_ms = round((perf_counter() - provider_started) * 1000)
                logger.info(
                    "roadmap_provider_search_completed generationId=%s competencyKey=%s "
                    "provider=%s status=%s resultCount=%d elapsedMs=%d",
                    self._generation_id,
                    competency.roadmapSkillKey,
                    provider.name,
                    status,
                    len(provider_resources),
                    elapsed_ms,
                    extra={
                        "event": "roadmap_provider_search_completed",
                        "generationId": self._generation_id,
                        "competencyKey": competency.roadmapSkillKey,
                        "provider": provider.name,
                        "status": status,
                        "resultCount": len(provider_resources),
                        "elapsedMs": elapsed_ms,
                    },
                )
            except Exception as exception:
                elapsed_ms = round((perf_counter() - provider_started) * 1000)
                logger.warning(
                    "roadmap_provider_search_completed generationId=%s competencyKey=%s "
                    "provider=%s status=FAILED resultCount=0 elapsedMs=%d errorType=%s",
                    self._generation_id,
                    competency.roadmapSkillKey,
                    provider.name,
                    elapsed_ms,
                    type(exception).__name__,
                    extra={
                        "event": "roadmap_provider_search_completed",
                        "generationId": self._generation_id,
                        "competencyKey": competency.roadmapSkillKey,
                        "provider": provider.name,
                        "status": "FAILED",
                        "resultCount": 0,
                        "elapsedMs": elapsed_ms,
                        "errorType": type(exception).__name__,
                    },
                )

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


__all__ = [
    "LearningResourceSearchService",
    "_official_provider",
    "_summarize",
]

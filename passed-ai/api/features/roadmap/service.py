import asyncio
import logging
from time import perf_counter
from uuid import uuid4

import httpx

from api.features.roadmap.assembler import assemble_roadmap_response
from api.features.roadmap.config import RoadmapSettings, get_roadmap_settings
from api.features.roadmap.content_generation import (
    FakeRoadmapContentGenerator,
    RoadmapContentGenerator,
    create_content_generator,
    generate_content,
)
from api.features.roadmap.planner import create_learning_stages
from api.features.roadmap.resources.recommendation import recommend_learning_resources
from api.features.roadmap.schema import (
    Competency,
    LearningStage,
    RoadmapGenerateRequest,
    RoadmapGenerateResponse,
)
from api.features.roadmap.validator import validate_generated_content


logger = logging.getLogger(__name__)


async def generate_roadmap(
    request: RoadmapGenerateRequest,
    generator: RoadmapContentGenerator | None = None,
    http_client: httpx.AsyncClient | None = None,
) -> RoadmapGenerateResponse:
    generation_id = uuid4().hex
    started = perf_counter()
    logger.info(
        "roadmap_generation_started generationId=%s competencyCount=%d",
        generation_id,
        len(request.competencies),
        extra={
            "event": "roadmap_generation_started",
            "generationId": generation_id,
            "competencyCount": len(request.competencies),
        },
    )
    try:
        settings = get_roadmap_settings()
        async with asyncio.timeout(settings.generation_total_timeout_seconds):
            if http_client is None:
                async with httpx.AsyncClient() as request_client:
                    response = await _generate_roadmap(
                        request, generator, generation_id, request_client, settings
                    )
            else:
                response = await _generate_roadmap(
                    request, generator, generation_id, http_client, settings
                )
    except Exception as exception:
        elapsed_ms = round((perf_counter() - started) * 1000)
        logger.error(
            "roadmap_generation_completed generationId=%s status=FAILED "
            "competencyCount=%d elapsedMs=%d errorType=%s errorMessage=%s",
            generation_id,
            len(request.competencies),
            elapsed_ms,
            type(exception).__name__,
            str(exception),
            exc_info=True,
            extra={
                "event": "roadmap_generation_completed",
                "generationId": generation_id,
                "status": "FAILED",
                "competencyCount": len(request.competencies),
                "elapsedMs": elapsed_ms,
                "errorType": type(exception).__name__,
                "errorMessage": str(exception),
            },
        )
        raise
    elapsed_ms = round((perf_counter() - started) * 1000)
    logger.info(
        "roadmap_generation_completed generationId=%s status=SUCCESS "
        "competencyCount=%d elapsedMs=%d",
        generation_id,
        len(request.competencies),
        elapsed_ms,
        extra={
            "event": "roadmap_generation_completed",
            "generationId": generation_id,
            "status": "SUCCESS",
            "competencyCount": len(request.competencies),
            "elapsedMs": elapsed_ms,
        },
    )
    return response

async def _generate_roadmap(
    request: RoadmapGenerateRequest,
    generator: RoadmapContentGenerator | None,
    generation_id: str,
    http_client: httpx.AsyncClient,
    settings: RoadmapSettings,
) -> RoadmapGenerateResponse:
    stages_by_key = _plan_learning_stages(request.competencies)
    generated = await generate_content(
        generator or create_content_generator(settings),
        request.competencies,
        stages_by_key,
        generation_id,
    )
    resources_by_key = await recommend_learning_resources(
        request.competencies,
        generated,
        generation_id,
        http_client,
        settings,
    )
    validate_generated_content(
        request.competencies, stages_by_key, resources_by_key, generated
    )
    return assemble_roadmap_response(
        request.competencies, generated, resources_by_key
    )


def _plan_learning_stages(
    competencies: list[Competency],
) -> dict[str, list[LearningStage]]:
    return {
        competency.roadmapSkillKey: create_learning_stages(competency)
        for competency in competencies
    }

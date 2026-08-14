import asyncio
import logging
from time import perf_counter

import httpx

from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.resources.provider import create_resource_providers
from api.features.roadmap.resources.query import build_competency_search_profiles
from api.features.roadmap.resources.recommender import (
    LearningResourceRecommender,
    RecommendationTarget,
    build_book_search_query,
    build_competency_book_query,
    build_competency_search_query,
    build_competency_web_query,
    build_milestone_search_query,
    build_web_search_query,
    classify_resource_relevance,
)
from api.features.roadmap.resources.search import LearningResourceSearchService
from api.features.roadmap.schema import (
    Competency,
    GeneratedResourceRecommendation,
    GeneratedRoadmapContent,
    LearningResource,
)


logger = logging.getLogger(__name__)


async def recommend_learning_resources(
    competencies: list[Competency],
    generated: GeneratedRoadmapContent,
    generation_id: str,
    http_client: httpx.AsyncClient,
    settings: RoadmapSettings,
) -> dict[str, list[LearningResource]]:
    resources_by_key: dict[str, list[LearningResource]] = {}

    search_started = perf_counter()
    search_service = LearningResourceSearchService(
        create_resource_providers(http_client, settings),
        enabled=settings.resource_search_enabled,
        max_concurrency=settings.resource_search_max_concurrency,
        generation_id=generation_id,
    )
    competency_by_key = {
        competency.roadmapSkillKey: competency
        for competency in competencies
    }
    search_profiles = await build_competency_search_profiles(
        competencies
    )
    targets = [
        RecommendationTarget(
            key=f"{skill.roadmapSkillKey}:{stage.startLevel}:{stage.targetLevel}:{index}",
            competency_name=competency_by_key[
                skill.roadmapSkillKey
            ].standardCompetencyName,
            competency_context=search_profiles[skill.roadmapSkillKey].context,
            title=milestone.title,
            learning_objective=milestone.learningObjective,
            completion_criteria=milestone.completionCriteria,
            candidates=[],
            distinctive_terms=search_profiles[
                skill.roadmapSkillKey
            ].distinctive_terms,
            excluded_terms=search_profiles[
                skill.roadmapSkillKey
            ].excluded_terms,
        )
        for skill in generated.skills
        for stage in skill.stages
        for index, milestone in enumerate(stage.milestones)
    ]
    target_competencies = {
        target.key: competency_by_key[target.key.rsplit(":", 3)[0]]
        for target in targets
    }

    async def search_for_competency(
        target: RecommendationTarget,
    ) -> list[LearningResource]:
        competency = target_competencies[target.key]
        resources = await search_service.search(
            competency,
            build_milestone_search_query(target),
            provider_queries={
                "kakao_book": build_book_search_query(target),
                "keenable": build_web_search_query(target),
            },
        )
        if not any(
            classify_resource_relevance(target, resource) > 0
            for resource in resources
        ):
            fallback = await search_service.search(
                competency,
                build_competency_search_query(target),
                provider_queries={
                    "kakao_book": build_competency_book_query(target),
                    "keenable": build_competency_web_query(target),
                },
            )
            resources = [*resources, *fallback]
        key = competency.roadmapSkillKey
        merged = {
            resource.resourceId: resource
            for resource in resources_by_key.get(key, [])
        }
        merged.update({resource.resourceId: resource for resource in resources})
        resources_by_key[key] = list(merged.values())
        return resources_by_key[key]

    competency_search_tasks: dict[str, asyncio.Task[list[LearningResource]]] = {}

    async def additional_search(
        target: RecommendationTarget,
    ) -> list[LearningResource]:
        competency_key = target.key.rsplit(":", 3)[0]
        task = competency_search_tasks.get(competency_key)
        if task is None:
            task = asyncio.create_task(search_for_competency(target))
            competency_search_tasks[competency_key] = task
        return await task

    recommendations = await LearningResourceRecommender(settings).recommend(
        targets, additional_search=additional_search
    )
    target_by_key = {target.key: target for target in targets}
    for skill in generated.skills:
        for stage in skill.stages:
            for index, milestone in enumerate(stage.milestones):
                target_key = (
                    f"{skill.roadmapSkillKey}:{stage.startLevel}:"
                    f"{stage.targetLevel}:{index}"
                )
                target = target_by_key[target_key]
                milestone.resourceRecommendations = [
                    GeneratedResourceRecommendation(
                        resourceId=resource.resourceId,
                        recommendationReason=resource.description,
                    )
                    for resource in recommendations[target.key]
                ]

    search_elapsed_ms = round((perf_counter() - search_started) * 1000)
    resource_count = sum(len(resources) for resources in resources_by_key.values())
    resource_description_char_count = sum(
        len(resource.description)
        for resources in resources_by_key.values()
        for resource in resources
    )
    logger.info(
        "roadmap_resource_search_completed generationId=%s competencyCount=%d "
        "resultCount=%d descriptionCharCount=%d elapsedMs=%d",
        generation_id,
        len(competencies),
        resource_count,
        resource_description_char_count,
        search_elapsed_ms,
        extra={
            "event": "roadmap_resource_search_completed",
            "generationId": generation_id,
            "competencyCount": len(competencies),
            "resultCount": resource_count,
            "descriptionCharCount": resource_description_char_count,
            "elapsedMs": search_elapsed_ms,
        },
    )
    return resources_by_key



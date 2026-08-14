import asyncio
import json

import httpx
from openai import AsyncOpenAI

from api.features.roadmap.config import get_roadmap_settings
from api.features.roadmap.exceptions import RoadmapConfigurationError, RoadmapGenerationError
from api.features.roadmap.resources.provider import create_resource_providers
from api.features.roadmap.resources.search import LearningResourceSearchService
from api.features.roadmap.resources.recommender import (
    LearningResourceRecommender,
    RecommendationTarget,
    build_book_search_query,
    build_milestone_search_query,
    build_web_search_query,
)
from api.features.roadmap.schema import (
    Competency,
    RequirementType,
    CompressedGroup,
    CompressedMilestoneContent,
    LearningResource,
    ReplanGroup,
    RoadmapReplanRequest,
    RoadmapReplanResponse,
)


SYSTEM_PROMPT = """You write one compressed learning milestone for a Korean job seeker.
The application has already selected and ordered the source milestones, assigned the exact study
time, and bound all identifiers. Do not return identifiers, order, levels, or estimated time.
Preserve the essential learning objectives and verifiable outcomes from every supplied source.
Combine overlapping theory and practice into one coherent, outcome-oriented activity. Write all
user-facing text in Korean. The completion criterion must name a concrete artifact, passing test,
measurable score, deployed result, or observable behavior. Return only the structured content.
"""


def _fake_content(group: ReplanGroup) -> CompressedMilestoneContent:
    first = group.sourceMilestones[0]
    return CompressedMilestoneContent(
        title=f"{group.skillName} 핵심 학습 압축 과정",
        description=" · ".join(item.title for item in group.sourceMilestones),
        learningObjective="; ".join(item.learningObjective for item in group.sourceMilestones),
        completionCriteria="; ".join(item.completionCriteria for item in group.sourceMilestones),
        milestoneType=first.milestoneType,
        difficulty=first.difficulty,
        compressionReason="인접한 학습 목표를 하나의 결과물 중심 과정으로 통합했습니다.",
    )


async def _generate_content(client: AsyncOpenAI, model: str,
                            group: ReplanGroup, instruction: str) -> CompressedMilestoneContent:
    payload = {
        "skillName": group.skillName,
        "assignedEstimatedMinutes": group.assignedEstimatedMinutes,
        "userInstruction": instruction,
        "sourceMilestones": [item.model_dump(mode="json") for item in group.sourceMilestones],
    }
    response = await client.responses.parse(
        model=model,
        input=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False, separators=(",", ":"))},
        ],
        text_format=CompressedMilestoneContent,
    )
    if response.output_parsed is None:
        raise RoadmapGenerationError("roadmap compression model returned no parsed output")
    return response.output_parsed


async def replan_roadmap(
    request: RoadmapReplanRequest, http_client: httpx.AsyncClient
) -> RoadmapReplanResponse:
    settings = get_roadmap_settings()
    if settings.generator == "fake":
        contents = [_fake_content(group) for group in request.groups]
    else:
        if not settings.openai_api_key:
            raise RoadmapConfigurationError(
                "OPENAI_API_KEY is required when ROADMAP_GENERATOR=llm"
            )
        client = AsyncOpenAI(
            api_key=settings.openai_api_key,
            timeout=settings.timeout_seconds,
            max_retries=0,
        )
        semaphore = asyncio.Semaphore(4)

        async def generate(group: ReplanGroup) -> CompressedMilestoneContent:
            async with semaphore:
                return await _generate_content(
                    client, settings.model, group, request.userInstruction
                )

        contents = await asyncio.gather(*(generate(group) for group in request.groups))

    search_service = LearningResourceSearchService(
        create_resource_providers(http_client, settings),
        enabled=settings.resource_search_enabled,
        max_concurrency=settings.resource_search_max_concurrency,
        generation_id=f"replan-{request.roadmapId}",
    )

    def competency_context(group: ReplanGroup) -> str:
        return " ".join((
            group.skillName,
            group.category.value.replace("_", " "),
            *(item.description for item in group.sourceMilestones),
            *(item.learningObjective for item in group.sourceMilestones),
        ))[:700]

    def competency(group: ReplanGroup) -> Competency:
        return Competency(
            roadmapSkillKey=group.groupKey,
            standardCompetencyId=group.standardCompetencyId,
            standardCompetencyName=group.skillName,
            category=group.category,
            currentLevel=group.currentLevel,
            targetLevel=group.targetLevel,
            requirementType=RequirementType.REQUIRED,
            gapLevel=max(group.targetLevel - group.currentLevel, 0),
            frequency=0,
            priority=0,
            sources=[],
        )

    targets = [
        RecommendationTarget(
            key=group.groupKey,
            competency_name=group.skillName,
            competency_context=competency_context(group),
            title=content.title,
            learning_objective=content.learningObjective,
            completion_criteria=content.completionCriteria,
            candidates=[],
        )
        for group, content in zip(request.groups, contents, strict=True)
    ]
    competency_by_key = {
        group.groupKey: competency(group) for group in request.groups
    }

    async def additional_search(
        target: RecommendationTarget,
    ) -> list[LearningResource]:
        return await search_service.search(
            competency_by_key[target.key],
            build_milestone_search_query(target),
            provider_queries={
                "kakao_book": build_book_search_query(target),
                "keenable": build_web_search_query(target),
            },
        )

    ranked = await LearningResourceRecommender(settings).recommend(
        targets, additional_search=additional_search
    )
    groups = [
        CompressedGroup(
            groupKey=group.groupKey,
            **content.model_dump(),
            learningResources=ranked[group.groupKey],
        )
        for group, content in zip(request.groups, contents, strict=True)
    ]
    return RoadmapReplanResponse(
        summary=f"{len(request.groups)}개의 핵심 학습 과정으로 로드맵을 압축했습니다.",
        groups=groups,
    )

import asyncio
import logging
from time import perf_counter
from typing import Protocol
from uuid import uuid4

import httpx

from api.features.roadmap.client import OpenAiRoadmapClient
from api.features.roadmap.config import RoadmapSettings, get_roadmap_settings
from api.features.roadmap.exceptions import RoadmapGenerationError
from api.features.roadmap.planner import create_learning_stages
from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    Difficulty,
    GeneratedLearningStage,
    GeneratedMilestoneContent,
    GeneratedRoadmapContent,
    GeneratedResourceRecommendation,
    GeneratedSkillContent,
    ModelGeneratedRoadmapContent,
    ModelGeneratedSkillContent,
    LearningResource,
    Milestone,
    LearningStage,
    MilestoneType,
    RoadmapGenerateRequest,
    RoadmapGenerateResponse,
    RoadmapSkill,
)
from api.features.roadmap.resource_search import LearningResourceSearchService
from api.features.roadmap.resource_provider import create_resource_providers
from api.features.roadmap.resource_recommender import (
    LearningResourceRecommender,
    RecommendationTarget,
    build_book_search_query,
    build_milestone_search_query,
)
from api.features.roadmap.resource_query import build_competency_ranking_contexts
from api.features.roadmap.validator import (
    remove_unknown_resource_recommendations,
    validate_generated_content,
)


logger = logging.getLogger(__name__)
CONTENT_GENERATION_MAX_CONCURRENCY = 4


class RoadmapContentGenerator(Protocol):
    async def generate(
        self,
        competencies: list[Competency],
        stages_by_key: dict[str, list[LearningStage]],
        resources_by_key: dict[str, list[LearningResource]],
    ) -> ModelGeneratedRoadmapContent: ...


class FakeRoadmapContentGenerator:
    async def generate(
        self,
        competencies: list[Competency],
        stages_by_key: dict[str, list[LearningStage]],
        resources_by_key: dict[str, list[LearningResource]],
    ) -> ModelGeneratedRoadmapContent:
        skills = []
        for competency in competencies:
            key = competency.roadmapSkillKey
            skills.append(
                ModelGeneratedSkillContent(
                    stages=[{"milestones": self._contents(
                        competency, stage, resources_by_key.get(key, [])
                    )} for stage in stages_by_key[key]]
                )
            )
        return ModelGeneratedRoadmapContent(skills=skills)

    def _contents(
        self, competency: Competency, stage: LearningStage,
        resources: list[LearningResource],
    ) -> list[GeneratedMilestoneContent]:
        name = competency.standardCompetencyName
        recommendations = [
            GeneratedResourceRecommendation(
                resourceId=resource.resourceId,
                recommendationReason=(
                    f"{name}의 수준 {stage.targetLevel} 학습 내용을 실습하고 "
                    "완료 기준을 점검하는 데 활용할 수 있는 자료입니다."
                ),
            )
            for resource in resources[:2]
        ]
        if competency.category == CompetencyCategory.CERTIFICATION:
            return [
                GeneratedMilestoneContent(
                    title=f"{name} 시험 범위 학습",
                    description=f"{name} 시험의 핵심 개념과 출제 범위를 학습한다.",
                    learningObjective=f"{name} 시험에 필요한 핵심 개념을 설명할 수 있다.",
                    completionCriteria=f"{name} 모의고사에서 목표 점수를 달성한다.",
                    milestoneType=MilestoneType.CERTIFICATION,
                    difficulty=Difficulty.BEGINNER,
                    estimatedMinutes=60,
                    resourceRecommendations=recommendations,
                ),
                GeneratedMilestoneContent(
                    title=f"{name} 모의고사 실전 연습",
                    description=f"{name} 모의고사를 풀고 오답을 분석한다.",
                    learningObjective=f"{name} 시험 유형별 문제를 시간 내에 해결할 수 있다.",
                    completionCriteria=f"{name} 모의고사에서 안정적으로 합격 기준을 넘는다.",
                    milestoneType=MilestoneType.CERTIFICATION,
                    difficulty=Difficulty.INTERMEDIATE,
                    estimatedMinutes=60,
                    resourceRecommendations=recommendations,
                ),
                GeneratedMilestoneContent(
                    title=f"{name} 자격 취득",
                    description=f"{name} 시험을 응시하고 자격 취득을 완료한다.",
                    learningObjective=f"{name} 시험 문제에 학습한 지식을 적용할 수 있다.",
                    completionCriteria=f"{name} 시험에 합격해 자격을 취득한다.",
                    milestoneType=MilestoneType.CERTIFICATION,
                    difficulty=Difficulty.INTERMEDIATE,
                    estimatedMinutes=60,
                    resourceRecommendations=recommendations,
                ),
            ]

        target = stage.targetLevel
        milestone_type = MilestoneType.PRACTICE if target == 2 else MilestoneType.PROJECT
        difficulty = Difficulty.INTERMEDIATE if target == 2 else Difficulty.ADVANCED
        return [
            GeneratedMilestoneContent(
                title=f"{name} 수준 {target} 핵심 학습",
                description=f"{name} 수준 {target}에 필요한 핵심 개념과 기능을 학습한다.",
                learningObjective=f"{name} 수준 {target}의 핵심 기능을 설명할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 핵심 기능을 실습으로 검증한다.",
                milestoneType=milestone_type,
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                resourceRecommendations=recommendations,
            ),
            GeneratedMilestoneContent(
                title=f"{name} 수준 {target} 단계별 실습",
                description=f"{name} 수준 {target}의 핵심 기능을 단계별로 적용한다.",
                learningObjective=f"{name} 수준 {target}의 기능을 스스로 적용할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 핵심 기능을 사용한 실습을 완료한다.",
                milestoneType=milestone_type,
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                resourceRecommendations=recommendations,
            ),
            GeneratedMilestoneContent(
                title=f"{name} 수준 {target} 실전 과제",
                description=f"{name}을 활용해 수준 {target}에 맞는 실무 과제를 수행한다.",
                learningObjective=f"{name} 수준 {target}의 작업을 수행할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 검증 가능한 결과물을 완성한다.",
                milestoneType=milestone_type,
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                resourceRecommendations=recommendations,
            ),
        ]


def _generator(settings: RoadmapSettings) -> RoadmapContentGenerator:
    if settings.generator == "llm":
        return OpenAiRoadmapClient(settings)
    return FakeRoadmapContentGenerator()


def build_roadmap_title(competencies: list[Competency]) -> str:
    names = [item.standardCompetencyName for item in competencies]
    if len(names) == 1:
        return f"{names[0]} 학습 로드맵"
    return f"{names[0]}·{names[1]} 중심 직무 역량 강화 로드맵"


async def _generate_content_in_batches(
    generator: RoadmapContentGenerator,
    competencies: list[Competency],
    stages_by_key: dict[str, list[LearningStage]],
    resources_by_key: dict[str, list[LearningResource]],
) -> GeneratedRoadmapContent:
    """Generate one skill per call; bind keys and stage bounds in application code."""
    semaphore = asyncio.Semaphore(CONTENT_GENERATION_MAX_CONCURRENCY)

    async def request_content(
        competency: Competency, stages: list[LearningStage]
    ) -> ModelGeneratedRoadmapContent:
        key = competency.roadmapSkillKey
        async with semaphore:
            return await generator.generate(
                [competency],
                {key: stages},
                {key: resources_by_key.get(key, [])},
            )

    def bind_skill(
        competency: Competency,
        stages: list[LearningStage],
        model_content: ModelGeneratedRoadmapContent,
    ) -> GeneratedSkillContent:
        if len(model_content.skills) != 1:
            raise ValueError("single competency generation must return exactly one skill")
        model_stages = model_content.skills[0].stages
        if len(model_stages) != len(stages):
            raise ValueError("generated learning stages do not match required stages")
        return GeneratedSkillContent(
            roadmapSkillKey=competency.roadmapSkillKey,
            stages=[
                GeneratedLearningStage(
                    startLevel=stage.startLevel,
                    targetLevel=stage.targetLevel,
                    milestones=model_stage.milestones,
                )
                for stage, model_stage in zip(stages, model_stages, strict=True)
            ],
        )

    def validate_skill(competency: Competency, skill: GeneratedSkillContent) -> None:
        skill_for_validation = skill.model_copy(deep=True)
        generated_for_validation = GeneratedRoadmapContent(
            title=build_roadmap_title([competency]),
            skills=[skill_for_validation],
        )
        # Unknown resource IDs are handled by the existing sanitization pass.
        # They should not force an otherwise valid batch through the slower fallback.
        remove_unknown_resource_recommendations(
            resources_by_key, generated_for_validation
        )
        validate_generated_content(
            [competency],
            {competency.roadmapSkillKey: stages_by_key[competency.roadmapSkillKey]},
            resources_by_key,
            generated_for_validation,
        )

    async def generate_stage(
        competency: Competency, stage: LearningStage
    ) -> GeneratedLearningStage:
        model_content = await request_content(competency, [stage])
        if len(model_content.skills) != 1:
            raise ValueError("single stage generation must return exactly one skill")
        model_stages = model_content.skills[0].stages
        if len(model_stages) != 1:
            raise ValueError("single stage generation must return exactly one stage")
        return GeneratedLearningStage(
            startLevel=stage.startLevel,
            targetLevel=stage.targetLevel,
            milestones=model_stages[0].milestones,
        )

    async def generate_skill(competency: Competency) -> GeneratedSkillContent:
        key = competency.roadmapSkillKey
        stages = stages_by_key[key]
        try:
            skill = bind_skill(
                competency, stages, await request_content(competency, stages)
            )
            validate_skill(competency, skill)
            return skill
        except (ValueError, RoadmapGenerationError) as exception:
            logger.warning(
                "roadmap_competency_batch_fallback competencyKey=%s errorType=%s "
                "errorMessage=%s",
                key, type(exception).__name__, str(exception),
                extra={
                    "event": "roadmap_competency_batch_fallback",
                    "competencyKey": key,
                    "errorType": type(exception).__name__,
                    "errorMessage": str(exception),
                },
            )
            generated_stages = await asyncio.gather(*(
                generate_stage(competency, stage) for stage in stages
            ))
            skill = GeneratedSkillContent(
                roadmapSkillKey=key, stages=generated_stages
            )
            validate_skill(competency, skill)
            return skill

    generated_skills = await asyncio.gather(*(
        generate_skill(competency) for competency in competencies
    ))

    return GeneratedRoadmapContent(
        title=build_roadmap_title(competencies),
        skills=generated_skills,
    )


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
    stages_by_key = {
        competency.roadmapSkillKey: create_learning_stages(competency)
        for competency in request.competencies
    }
    resources_by_key: dict[str, list[LearningResource]] = {}
    content_generator = generator or _generator(settings)
    generator_started = perf_counter()
    generated = await _generate_content_in_batches(
        content_generator,
        request.competencies,
        stages_by_key,
        {},
    )
    generator_elapsed_ms = round((perf_counter() - generator_started) * 1000)
    logger.info(
        "roadmap_content_generation_completed generationId=%s generator=%s "
        "elapsedMs=%d",
        generation_id,
        type(content_generator).__name__,
        generator_elapsed_ms,
        extra={
            "event": "roadmap_content_generation_completed",
            "generationId": generation_id,
            "generator": type(content_generator).__name__,
            "elapsedMs": generator_elapsed_ms,
        },
    )

    search_started = perf_counter()
    search_service = LearningResourceSearchService(
        create_resource_providers(http_client, settings),
        enabled=settings.resource_search_enabled,
        max_concurrency=settings.resource_search_max_concurrency,
        generation_id=generation_id,
    )
    competency_by_key = {
        competency.roadmapSkillKey: competency
        for competency in request.competencies
    }
    ranking_contexts = await build_competency_ranking_contexts(
        request.competencies
    )
    targets = [
        RecommendationTarget(
            key=f"{skill.roadmapSkillKey}:{stage.startLevel}:{stage.targetLevel}:{index}",
            competency_name=competency_by_key[
                skill.roadmapSkillKey
            ].standardCompetencyName,
            competency_context=ranking_contexts[skill.roadmapSkillKey],
            title=milestone.title,
            learning_objective=milestone.learningObjective,
            completion_criteria=milestone.completionCriteria,
            candidates=[],
        )
        for skill in generated.skills
        for stage in skill.stages
        for index, milestone in enumerate(stage.milestones)
    ]
    target_competencies = {
        target.key: competency_by_key[target.key.rsplit(":", 3)[0]]
        for target in targets
    }

    async def additional_search(
        target: RecommendationTarget,
    ) -> list[LearningResource]:
        competency = target_competencies[target.key]
        resources = await search_service.search(
            competency,
            build_milestone_search_query(target),
            provider_queries={"kakao_book": build_book_search_query(target)},
        )
        key = competency.roadmapSkillKey
        merged = {
            resource.resourceId: resource
            for resource in resources_by_key.get(key, [])
        }
        merged.update({resource.resourceId: resource for resource in resources})
        resources_by_key[key] = list(merged.values())
        return resources

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
        len(request.competencies),
        resource_count,
        resource_description_char_count,
        search_elapsed_ms,
        extra={
            "event": "roadmap_resource_search_completed",
            "generationId": generation_id,
            "competencyCount": len(request.competencies),
            "resultCount": resource_count,
            "descriptionCharCount": resource_description_char_count,
            "elapsedMs": search_elapsed_ms,
        },
    )
    validate_generated_content(
        request.competencies, stages_by_key, resources_by_key, generated
    )

    generated_by_key = {item.roadmapSkillKey: item for item in generated.skills}
    skills = []
    for competency in request.competencies:
        key = competency.roadmapSkillKey
        content = generated_by_key[key]
        resources = {
            resource.resourceId: resource for resource in resources_by_key.get(key, [])
        }
        milestones = []
        learning_order = 1
        for generated_stage in content.stages:
            for item in generated_stage.milestones:
                milestones.append(
                    Milestone(
                        **item.model_dump(exclude={"resourceRecommendations"}),
                        startLevel=generated_stage.startLevel,
                        targetLevel=generated_stage.targetLevel,
                        learningOrder=learning_order,
                        learningResources=[
                            resources[recommendation.resourceId].model_copy(update={
                                "description": recommendation.recommendationReason
                            })
                            for recommendation in item.resourceRecommendations
                        ],
                    )
                )
                learning_order += 1
        skills.append(RoadmapSkill(roadmapSkillKey=key, milestones=milestones))

    return RoadmapGenerateResponse(title=generated.title, skills=skills)

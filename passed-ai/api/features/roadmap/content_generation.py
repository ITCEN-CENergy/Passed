import asyncio
import logging
from time import perf_counter
from typing import Protocol

from api.features.roadmap.client import OpenAiRoadmapClient
from api.features.roadmap.config import RoadmapSettings
from api.features.roadmap.exceptions import RoadmapGenerationError
from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    Difficulty,
    GeneratedLearningStage,
    GeneratedMilestoneContent,
    GeneratedResourceRecommendation,
    GeneratedRoadmapContent,
    GeneratedSkillContent,
    LearningResource,
    LearningStage,
    MilestoneType,
    ModelGeneratedLearningStageContent,
    ModelGeneratedMilestoneContent,
    ModelGeneratedRoadmapContent,
    ModelGeneratedSkillContent,
)
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
                    stages=[
                        ModelGeneratedLearningStageContent(
                            milestones=self._contents(
                                competency,
                                stage,
                                resources_by_key.get(key, []),
                            )
                        )
                        for stage in stages_by_key[key]
                    ]
                )
            )
        return ModelGeneratedRoadmapContent(skills=skills)

    def _contents(
        self, competency: Competency, stage: LearningStage,
        resources: list[LearningResource],
    ) -> list[ModelGeneratedMilestoneContent]:
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
                ModelGeneratedMilestoneContent(
                    title=f"{name} 시험 범위 학습",
                    description=f"{name} 시험의 핵심 개념과 출제 범위를 학습한다.",
                    learningObjective=f"{name} 시험에 필요한 핵심 개념을 설명할 수 있다.",
                    completionCriteria=f"{name} 모의고사에서 목표 점수를 달성한다.",
                    difficulty=Difficulty.BEGINNER,
                    estimatedMinutes=60,
                    required=True,
                    resourceRecommendations=recommendations,
                ),
                ModelGeneratedMilestoneContent(
                    title=f"{name} 모의고사 실전 연습",
                    description=f"{name} 모의고사를 풀고 오답을 분석한다.",
                    learningObjective=f"{name} 시험 유형별 문제를 시간 내에 해결할 수 있다.",
                    completionCriteria=f"{name} 모의고사에서 안정적으로 합격 기준을 넘는다.",
                    difficulty=Difficulty.INTERMEDIATE,
                    estimatedMinutes=60,
                    required=False,
                    resourceRecommendations=recommendations,
                ),
                ModelGeneratedMilestoneContent(
                    title=f"{name} 자격 취득",
                    description=f"{name} 시험을 응시하고 자격 취득을 완료한다.",
                    learningObjective=f"{name} 시험 문제에 학습한 지식을 적용할 수 있다.",
                    completionCriteria=f"{name} 시험에 합격해 자격을 취득한다.",
                    difficulty=Difficulty.INTERMEDIATE,
                    estimatedMinutes=60,
                    required=True,
                    resourceRecommendations=recommendations,
                ),
            ]

        target = stage.targetLevel
        difficulty = Difficulty.INTERMEDIATE if target == 2 else Difficulty.ADVANCED
        return [
            ModelGeneratedMilestoneContent(
                title=f"{name} 수준 {target} 핵심 학습",
                description=f"{name} 수준 {target}에 필요한 핵심 개념과 기능을 학습한다.",
                learningObjective=f"{name} 수준 {target}의 핵심 기능을 설명할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 핵심 기능을 실습으로 검증한다.",
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                required=True,
                resourceRecommendations=recommendations,
            ),
            ModelGeneratedMilestoneContent(
                title=f"{name} 수준 {target} 단계별 실습",
                description=f"{name} 수준 {target}의 핵심 기능을 단계별로 적용한다.",
                learningObjective=f"{name} 수준 {target}의 기능을 스스로 적용할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 핵심 기능을 사용한 실습을 완료한다.",
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                required=False,
                resourceRecommendations=recommendations,
            ),
            ModelGeneratedMilestoneContent(
                title=f"{name} 수준 {target} 실전 과제",
                description=f"{name}을 활용해 수준 {target}에 맞는 실무 과제를 수행한다.",
                learningObjective=f"{name} 수준 {target}의 작업을 수행할 수 있다.",
                completionCriteria=f"{name} 수준 {target}의 검증 가능한 결과물을 완성한다.",
                difficulty=difficulty,
                estimatedMinutes=target * 60,
                required=True,
                resourceRecommendations=recommendations,
            ),
        ]


def create_content_generator(settings: RoadmapSettings) -> RoadmapContentGenerator:
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
                    milestones=bind_milestone_types(
                        competency, stage, model_stage.milestones
                    ),
                )
                for stage, model_stage in zip(stages, model_stages, strict=True)
            ],
        )

    def bind_milestone_types(
        competency: Competency,
        stage: LearningStage,
        milestones: list[ModelGeneratedMilestoneContent],
    ) -> list[GeneratedMilestoneContent]:
        """Assign the application-owned milestone type from validated inputs."""
        if competency.category == CompetencyCategory.CERTIFICATION:
            expected_type = MilestoneType.CERTIFICATION
        else:
            expected_type = (
                MilestoneType.PRACTICE
                if stage.targetLevel <= 2
                else MilestoneType.PROJECT
            )
        return [GeneratedMilestoneContent.model_validate({
            **milestone.model_dump(),
            "milestoneType": expected_type,
        }) for milestone in milestones]

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
            milestones=bind_milestone_types(
                competency, stage, model_stages[0].milestones
            ),
        )

    async def generate_skill(competency: Competency) -> GeneratedSkillContent:
        key = competency.roadmapSkillKey
        stages = stages_by_key[key]
        if len(stages) > 2:
            generated_stages = await asyncio.gather(*(
                generate_stage(competency, stage) for stage in stages
            ))
            skill = GeneratedSkillContent(
                roadmapSkillKey=key, stages=generated_stages
            )
            validate_skill(competency, skill)
            return skill
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



async def generate_content(
    content_generator: RoadmapContentGenerator,
    competencies: list[Competency],
    stages_by_key: dict[str, list[LearningStage]],
    generation_id: str,
) -> GeneratedRoadmapContent:
    generator_started = perf_counter()
    generated = await _generate_content_in_batches(
        content_generator,
        competencies,
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
    return generated




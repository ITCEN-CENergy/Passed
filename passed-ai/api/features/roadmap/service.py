from typing import Protocol

from api.features.roadmap.client import OpenAiRoadmapClient
from api.features.roadmap.config import get_roadmap_settings
from api.features.roadmap.planner import create_milestone_slots
from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    Difficulty,
    GeneratedMilestoneContent,
    GeneratedRoadmapContent,
    GeneratedSkillContent,
    Milestone,
    MilestoneSlot,
    MilestoneType,
    RoadmapGenerateRequest,
    RoadmapGenerateResponse,
    RoadmapSkill,
)
from api.features.roadmap.validator import validate_generated_content


class RoadmapContentGenerator(Protocol):
    def generate(
        self,
        competencies: list[Competency],
        slots_by_key: dict[str, list[MilestoneSlot]],
    ) -> GeneratedRoadmapContent: ...


class FakeRoadmapContentGenerator:
    def generate(
        self,
        competencies: list[Competency],
        slots_by_key: dict[str, list[MilestoneSlot]],
    ) -> GeneratedRoadmapContent:
        skills = []
        for competency in competencies:
            contents = [
                self._content(competency, slot)
                for slot in slots_by_key[competency.roadmapSkillKey]
            ]
            skills.append(
                GeneratedSkillContent(
                    roadmapSkillKey=competency.roadmapSkillKey, milestones=contents
                )
            )
        return GeneratedRoadmapContent(title="개인 맞춤 역량 강화 로드맵", skills=skills)

    def _content(
        self, competency: Competency, slot: MilestoneSlot
    ) -> GeneratedMilestoneContent:
        name = competency.standardCompetencyName
        if competency.category == CompetencyCategory.CERTIFICATION:
            return GeneratedMilestoneContent(
                title=f"{name} 자격 취득",
                description=f"{name} 시험 범위를 학습하고 자격 취득을 준비한다.",
                learningObjective=f"{name} 시험에 필요한 지식과 기술을 적용할 수 있다.",
                completionCriteria=f"{name} 시험에 합격해 자격을 취득한다.",
                milestoneType=MilestoneType.CERTIFICATION,
                difficulty=Difficulty.BEGINNER,
                estimatedMinutes=60,
            )

        target = slot.targetLevel
        milestone_type = MilestoneType.PRACTICE if target == 2 else MilestoneType.PROJECT
        difficulty = Difficulty.INTERMEDIATE if target == 2 else Difficulty.ADVANCED
        return GeneratedMilestoneContent(
            title=f"{name} 수준 {target} 달성",
            description=f"{name}을 활용해 수준 {target}에 맞는 실무 과제를 수행한다.",
            learningObjective=f"{name} 수준 {target}의 작업을 수행할 수 있다.",
            completionCriteria=f"{name} 수준 {target}의 검증 가능한 결과물을 완성한다.",
            milestoneType=milestone_type,
            difficulty=difficulty,
            estimatedMinutes=target * 60,
        )


def _generator() -> RoadmapContentGenerator:
    settings = get_roadmap_settings()
    if settings.generator == "llm":
        return OpenAiRoadmapClient(settings)
    return FakeRoadmapContentGenerator()


def generate_roadmap(
    request: RoadmapGenerateRequest,
    generator: RoadmapContentGenerator | None = None,
) -> RoadmapGenerateResponse:
    slots_by_key = {
        competency.roadmapSkillKey: create_milestone_slots(competency)
        for competency in request.competencies
    }
    generated = (generator or _generator()).generate(request.competencies, slots_by_key)
    validate_generated_content(request.competencies, slots_by_key, generated)

    generated_by_key = {item.roadmapSkillKey: item for item in generated.skills}
    skills = []
    for competency in request.competencies:
        key = competency.roadmapSkillKey
        content = generated_by_key[key]
        milestones = [
            Milestone(
                **item.model_dump(),
                startLevel=slot.startLevel,
                targetLevel=slot.targetLevel,
                learningOrder=slot.learningOrder,
            )
            for item, slot in zip(content.milestones, slots_by_key[key], strict=True)
        ]
        skills.append(RoadmapSkill(roadmapSkillKey=key, milestones=milestones))

    return RoadmapGenerateResponse(title=generated.title, skills=skills)

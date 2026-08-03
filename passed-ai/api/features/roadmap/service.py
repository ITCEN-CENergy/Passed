from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    Difficulty,
    Milestone,
    MilestoneType,
    RoadmapGenerateRequest,
    RoadmapGenerateResponse,
    RoadmapSkill,
)


def _difficulty(target_level: int) -> Difficulty:
    if target_level == 1:
        return Difficulty.BEGINNER
    if target_level == 2:
        return Difficulty.INTERMEDIATE
    return Difficulty.ADVANCED


def _milestone_type(target_level: int) -> MilestoneType:
    if target_level == 1:
        return MilestoneType.CONCEPT
    if target_level == 2:
        return MilestoneType.PRACTICE
    return MilestoneType.PROJECT


def _general_milestones(competency: Competency) -> list[Milestone]:
    name = competency.standardCompetencyName
    return [
        Milestone(
            title=f"{name} 목표 수준 {target_level} 학습",
            description=(
                f"{name}의 수준 {target_level - 1}에서 수준 {target_level}로 "
                "향상하기 위한 학습을 수행한다."
            ),
            learningObjective=f"{name} 수준 {target_level}에 해당하는 작업을 수행할 수 있다.",
            completionCriteria=f"{name} 수준 {target_level}의 실습 결과물을 완성한다.",
            startLevel=target_level - 1,
            targetLevel=target_level,
            milestoneType=_milestone_type(target_level),
            difficulty=_difficulty(target_level),
            estimatedMinutes=target_level * 60,
            learningOrder=order,
        )
        for order, target_level in enumerate(
            range(competency.currentLevel + 1, competency.targetLevel + 1), start=1
        )
    ]


def _certification_milestone(competency: Competency) -> Milestone:
    name = competency.standardCompetencyName
    return Milestone(
        title=f"{name} 자격 취득",
        description=f"{name} 자격 취득을 위한 학습을 수행한다.",
        learningObjective=f"{name} 자격 시험에 필요한 지식과 기술을 적용할 수 있다.",
        completionCriteria=f"{name} 자격 취득 요건을 충족한다.",
        startLevel=0,
        targetLevel=1,
        milestoneType=MilestoneType.CERTIFICATION,
        difficulty=Difficulty.BEGINNER,
        estimatedMinutes=60,
        learningOrder=1,
    )


def generate_roadmap(request: RoadmapGenerateRequest) -> RoadmapGenerateResponse:
    skills = []
    for competency in request.competencies:
        milestones = (
            [_certification_milestone(competency)]
            if competency.category == CompetencyCategory.CERTIFICATION
            else _general_milestones(competency)
        )
        skills.append(
            RoadmapSkill(
                roadmapSkillKey=competency.roadmapSkillKey,
                milestones=milestones,
            )
        )

    return RoadmapGenerateResponse(
        title="개인 맞춤 역량 강화 로드맵",
        skills=skills,
    )

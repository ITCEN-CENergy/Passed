from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    GeneratedRoadmapContent,
    MilestoneSlot,
    MilestoneType,
)


def validate_generated_content(
    competencies: list[Competency],
    slots_by_key: dict[str, list[MilestoneSlot]],
    generated: GeneratedRoadmapContent,
) -> None:
    requested = {item.roadmapSkillKey: item for item in competencies}
    returned: set[str] = set()

    for skill in generated.skills:
        if skill.roadmapSkillKey in returned:
            raise ValueError("duplicate roadmapSkillKey in generated content")
        returned.add(skill.roadmapSkillKey)
        competency = requested.get(skill.roadmapSkillKey)
        if competency is None:
            raise ValueError("unexpected roadmapSkillKey in generated content")
        if len(skill.milestones) != len(slots_by_key[skill.roadmapSkillKey]):
            raise ValueError("generated milestone count does not match planned slots")

        titles: set[str] = set()
        for milestone in skill.milestones:
            if milestone.title in titles:
                raise ValueError("duplicate milestone title")
            titles.add(milestone.title)
            is_certification = competency.category == CompetencyCategory.CERTIFICATION
            if is_certification != (milestone.milestoneType == MilestoneType.CERTIFICATION):
                raise ValueError("milestoneType does not match competency category")

    if returned != set(requested):
        raise ValueError("not all requested roadmapSkillKeys were returned")

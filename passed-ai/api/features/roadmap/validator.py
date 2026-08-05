from api.features.roadmap.schema import (
    Competency,
    CompetencyCategory,
    GeneratedRoadmapContent,
    LearningResource,
    LearningStage,
    MilestoneType,
)


def validate_generated_content(
    competencies: list[Competency],
    stages_by_key: dict[str, list[LearningStage]],
    resources_by_key: dict[str, list[LearningResource]],
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
        required_stages = stages_by_key[skill.roadmapSkillKey]
        if len(skill.stages) != len(required_stages):
            raise ValueError("generated learning stages do not match required stages")

        titles: set[str] = set()
        allowed_resource_ids = {
            resource.resourceId
            for resource in resources_by_key.get(skill.roadmapSkillKey, [])
        }
        for generated_stage, required_stage in zip(
            skill.stages, required_stages, strict=True
        ):
            if (
                generated_stage.startLevel != required_stage.startLevel
                or generated_stage.targetLevel != required_stage.targetLevel
            ):
                raise ValueError("generated learning stage does not match required stage")
            for milestone in generated_stage.milestones:
                if milestone.title in titles:
                    raise ValueError("duplicate milestone title")
                titles.add(milestone.title)
                resource_ids = [
                    item.resourceId for item in milestone.resourceRecommendations
                ]
                if len(resource_ids) != len(set(resource_ids)):
                    raise ValueError("duplicate resourceId in milestone")
                if not set(resource_ids) <= allowed_resource_ids:
                    raise ValueError("milestone references an unknown resourceId")
                is_certification = (
                    competency.category == CompetencyCategory.CERTIFICATION
                )
                if is_certification != (
                    milestone.milestoneType == MilestoneType.CERTIFICATION
                ):
                    raise ValueError("milestoneType does not match competency category")

    if returned != set(requested):
        raise ValueError("not all requested roadmapSkillKeys were returned")

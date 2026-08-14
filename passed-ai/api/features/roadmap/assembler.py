from api.features.roadmap.schema import (
    Competency,
    GeneratedRoadmapContent,
    LearningResource,
    Milestone,
    RoadmapGenerateResponse,
    RoadmapSkill,
)


def assemble_roadmap_response(
    competencies: list[Competency],
    generated: GeneratedRoadmapContent,
    resources_by_key: dict[str, list[LearningResource]],
) -> RoadmapGenerateResponse:

    generated_by_key = {item.roadmapSkillKey: item for item in generated.skills}
    skills = []
    for competency in competencies:
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


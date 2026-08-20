from api.features.roadmap.schema import (
    Difficulty,
    GeneratedLearningStage,
    GeneratedMilestoneContent,
    GeneratedResourceRecommendation,
    GeneratedRoadmapContent,
    GeneratedSkillContent,
    LearningResource,
    MilestoneType,
)
from api.features.roadmap.validator import remove_unknown_resource_recommendations


def test_removes_unknown_resource_recommendations_and_preserves_known_ones() -> None:
    known = LearningResource(
        resourceId="known-resource",
        resourceType="BOOK",
        title="Docker",
        provider="Publisher",
        url="https://example.com/docker",
    )
    milestone = GeneratedMilestoneContent(
        title="Docker practice",
        description="Practice Docker and produce a container image.",
        learningObjective="Build a container image.",
        completionCriteria="Submit a working image.",
        milestoneType=MilestoneType.PRACTICE,
        difficulty=Difficulty.BEGINNER,
        estimatedMinutes=60,
        required=True,
        resourceRecommendations=[
            GeneratedResourceRecommendation(
                resourceId="known-resource", recommendationReason="Useful practice"
            ),
            GeneratedResourceRecommendation(
                resourceId="hallucinated-resource", recommendationReason="Not supplied"
            ),
        ],
    )
    generated = GeneratedRoadmapContent(
        title="Roadmap",
        skills=[GeneratedSkillContent(
            roadmapSkillKey="docker",
            stages=[GeneratedLearningStage(
                startLevel=1,
                targetLevel=2,
                milestones=[milestone.model_copy(deep=True) for _ in range(3)],
            )],
        )],
    )

    removed = remove_unknown_resource_recommendations(
        {"docker": [known]}, generated
    )

    assert removed == 3
    assert [
        item.resourceId
        for item in generated.skills[0].stages[0].milestones[0].resourceRecommendations
    ] == ["known-resource"]

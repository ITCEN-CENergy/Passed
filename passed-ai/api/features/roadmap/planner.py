from api.features.roadmap.schema import Competency, CompetencyCategory, LearningStage


def create_learning_stages(competency: Competency) -> list[LearningStage]:
    if competency.category == CompetencyCategory.CERTIFICATION:
        return [LearningStage(
            startLevel=competency.currentLevel,
            targetLevel=competency.targetLevel,
        )]

    if competency.currentLevel == competency.targetLevel:
        return [LearningStage(
            startLevel=competency.currentLevel,
            targetLevel=competency.targetLevel,
        )]

    return [
        LearningStage(
            startLevel=target_level - 1,
            targetLevel=target_level,
        )
        for target_level in range(
            competency.currentLevel + 1, competency.targetLevel + 1
        )
    ]

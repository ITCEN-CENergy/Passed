from api.features.roadmap.schema import Competency, CompetencyCategory, MilestoneSlot


def create_milestone_slots(competency: Competency) -> list[MilestoneSlot]:
    if competency.category == CompetencyCategory.CERTIFICATION:
        return [MilestoneSlot(startLevel=0, targetLevel=1, learningOrder=1)]

    return [
        MilestoneSlot(
            startLevel=target_level - 1,
            targetLevel=target_level,
            learningOrder=order,
        )
        for order, target_level in enumerate(
            range(competency.currentLevel + 1, competency.targetLevel + 1), start=1
        )
    ]

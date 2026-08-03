package com.cenergy.passed_backend.domain.roadmap.ai.model;

import java.util.List;

public record ValidatedRoadmapSkill(
        String roadmapSkillKey,
        List<ValidatedRoadmapMilestone> milestones
) {
    public ValidatedRoadmapSkill {
        milestones = List.copyOf(milestones);
    }
}

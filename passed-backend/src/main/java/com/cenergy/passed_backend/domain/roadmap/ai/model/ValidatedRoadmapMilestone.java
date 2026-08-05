package com.cenergy.passed_backend.domain.roadmap.ai.model;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

import java.util.List;

public record ValidatedRoadmapMilestone(
        String title,
        String description,
        String learningObjective,
        String completionCriteria,
        int startLevel,
        int targetLevel,
        MilestoneType milestoneType,
        Difficulty difficulty,
        int estimatedMinutes,
        int learningOrder,
        List<ValidatedLearningResource> learningResources
) {
    public ValidatedRoadmapMilestone {
        learningResources = List.copyOf(learningResources);
    }

    public ValidatedRoadmapMilestone(String title, String description, String learningObjective,
                                     String completionCriteria, int startLevel, int targetLevel,
                                     MilestoneType milestoneType, Difficulty difficulty,
                                     int estimatedMinutes, int learningOrder) {
        this(title, description, learningObjective, completionCriteria, startLevel, targetLevel,
                milestoneType, difficulty, estimatedMinutes, learningOrder, List.of());
    }
}

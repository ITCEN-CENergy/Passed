package com.cenergy.passed_backend.domain.roadmap.ai.model;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

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
        int learningOrder
) {
}

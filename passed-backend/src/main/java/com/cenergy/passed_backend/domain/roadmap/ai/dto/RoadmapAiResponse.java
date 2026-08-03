package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

import java.util.List;

public record RoadmapAiResponse(
        String title,
        List<Skill> skills
) {
    public record Skill(
            String roadmapSkillKey,
            List<Milestone> milestones
    ) {
    }

    public record Milestone(
            String title,
            String description,
            String learningObjective,
            String completionCriteria,
            Integer startLevel,
            Integer targetLevel,
            MilestoneType milestoneType,
            Difficulty difficulty,
            Integer estimatedMinutes,
            Integer learningOrder
    ) {
    }
}

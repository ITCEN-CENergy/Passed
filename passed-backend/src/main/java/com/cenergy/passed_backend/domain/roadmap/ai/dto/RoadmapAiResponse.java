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
            Integer learningOrder,
            Boolean required,
            List<LearningResource> learningResources
    ) {
        public Milestone {
            learningResources = learningResources == null ? List.of() : List.copyOf(learningResources);
        }

        public Milestone(String title, String description, String learningObjective,
                         String completionCriteria, Integer startLevel, Integer targetLevel,
                         MilestoneType milestoneType, Difficulty difficulty,
                         Integer estimatedMinutes, Integer learningOrder,
                         List<LearningResource> learningResources) {
            this(title, description, learningObjective, completionCriteria, startLevel, targetLevel,
                    milestoneType, difficulty, estimatedMinutes, learningOrder, true, learningResources);
        }

        public Milestone(String title, String description, String learningObjective,
                         String completionCriteria, Integer startLevel, Integer targetLevel,
                         MilestoneType milestoneType, Difficulty difficulty,
                         Integer estimatedMinutes, Integer learningOrder) {
            this(title, description, learningObjective, completionCriteria, startLevel, targetLevel,
                    milestoneType, difficulty, estimatedMinutes, learningOrder, true, List.of());
        }
    }

    public record LearningResource(
            String resourceId,
            String resourceType,
            String title,
            String description,
            String provider,
            String url,
            String thumbnailUrl,
            List<String> authors,
            Boolean isFree
    ) {
        public LearningResource {
            authors = authors == null ? List.of() : List.copyOf(authors);
        }
    }
}

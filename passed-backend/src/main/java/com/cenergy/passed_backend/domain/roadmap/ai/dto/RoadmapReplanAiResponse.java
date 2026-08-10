package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

import java.util.List;

public record RoadmapReplanAiResponse(String summary, List<CompressedGroup> groups) {
    public record CompressedGroup(
            String groupKey,
            String title,
            String description,
            String learningObjective,
            String completionCriteria,
            MilestoneType milestoneType,
            Difficulty difficulty,
            String compressionReason,
            List<LearningResource> learningResources
    ) {
        public CompressedGroup {
            learningResources = learningResources == null ? List.of() : List.copyOf(learningResources);
        }
    }

    public record LearningResource(String resourceId, String resourceType, String title,
                                   String description, String provider, String url,
                                   String thumbnailUrl) { }
}

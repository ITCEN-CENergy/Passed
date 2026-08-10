package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

import java.util.List;

public record RoadmapCompressionPlan(String summary, List<Group> groups) {
    public RoadmapCompressionPlan { groups = List.copyOf(groups); }

    public record Group(String groupKey, Long roadmapSkillId, List<Long> sourceMilestoneIds,
                        int assignedEstimatedMinutes, int learningOrder,
                        int startLevel, int targetLevel, String title, String description,
                        String learningObjective, String completionCriteria,
                        MilestoneType milestoneType, Difficulty difficulty,
                        String compressionReason, List<Resource> learningResources) {
        public Group {
            sourceMilestoneIds = List.copyOf(sourceMilestoneIds);
            learningResources = learningResources == null ? List.of() : List.copyOf(learningResources);
        }
    }

    public record Resource(String externalId, String resourceType, String title,
                           String description, String provider, String url,
                           String thumbnailUrl) { }
}

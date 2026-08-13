package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;

import java.util.List;

public record RoadmapCompressionPlan(String summary, List<Group> groups, List<SourceSnapshot> sourceSnapshot) {
    public RoadmapCompressionPlan {
        groups = List.copyOf(groups);
        sourceSnapshot = sourceSnapshot == null ? List.of() : List.copyOf(sourceSnapshot);
    }

    public RoadmapCompressionPlan(String summary, List<Group> groups) {
        this(summary, groups, List.of());
    }

    public record SourceSnapshot(Long linkId, Long roadmapSkillId, Long milestoneId,
                                 int learningOrder, boolean required, MilestoneStatus status) { }

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

package com.cenergy.passed_backend.domain.roadmap.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoadmapReplanPreviewResponse(
        Long roadmapId,
        UUID replanToken,
        String summary,
        int previousRemainingMinutes,
        int replannedRemainingMinutes,
        LocalDate previousEstimatedEndDate,
        LocalDate replannedEstimatedEndDate,
        List<CompressedSkill> skills
) {
    public RoadmapReplanPreviewResponse {
        skills = List.copyOf(skills);
    }

    public record CompressedSkill(Long roadmapSkillId, List<CompressedMilestone> milestones) {
        public CompressedSkill {
            milestones = List.copyOf(milestones);
        }
    }

    public record CompressedMilestone(List<Long> sourceMilestoneIds, String title,
                                      String description, String learningObjective,
                                      String completionCriteria, int estimatedMinutes,
                                      int learningOrder, String compressionReason,
                                      List<LearningResource> learningResources) {
        public CompressedMilestone {
            sourceMilestoneIds = List.copyOf(sourceMilestoneIds);
            learningResources = List.copyOf(learningResources);
        }
    }

    public record LearningResource(String externalId, String resourceType, String title,
                                   String description, String provider, String url,
                                   String thumbnailUrl) {
    }
}

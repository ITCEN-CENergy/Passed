package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;

import java.util.List;

public record RoadmapReplanAiRequest(
        Long roadmapId,
        String title,
        long delayDays,
        String userInstruction,
        List<Milestone> milestones
) {
    public RoadmapReplanAiRequest {
        milestones = List.copyOf(milestones);
    }

    public record Milestone(Long milestoneId, Long roadmapSkillId, String title,
                            MilestoneStatus status, int estimatedMinutes,
                            int learningOrder, boolean required) {
    }
}

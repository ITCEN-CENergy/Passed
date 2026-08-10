package com.cenergy.passed_backend.domain.roadmap.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MilestoneCompletionResponse(
        Long milestoneId,
        boolean completed,
        BigDecimal previousProgress,
        BigDecimal currentProgress,
        MilestoneStatus status,
        OffsetDateTime completedAt
) {
}

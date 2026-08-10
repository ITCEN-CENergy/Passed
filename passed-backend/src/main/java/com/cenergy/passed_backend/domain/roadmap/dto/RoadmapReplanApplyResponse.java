package com.cenergy.passed_backend.domain.roadmap.dto;

import java.time.LocalDate;

public record RoadmapReplanApplyResponse(
        Long roadmapId,
        int totalEstimatedMinutes,
        LocalDate estimatedEndDate
) {
}

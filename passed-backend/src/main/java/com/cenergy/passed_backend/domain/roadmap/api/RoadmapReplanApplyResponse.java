package com.cenergy.passed_backend.domain.roadmap.api;

import java.time.LocalDate;

public record RoadmapReplanApplyResponse(
        Long roadmapId,
        int totalEstimatedMinutes,
        LocalDate estimatedEndDate
) {
}

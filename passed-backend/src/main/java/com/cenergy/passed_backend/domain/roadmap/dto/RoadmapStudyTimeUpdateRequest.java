package com.cenergy.passed_backend.domain.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoadmapStudyTimeUpdateRequest(
        @Min(30) @Max(480) int dailyStudyMinutes
) {
}

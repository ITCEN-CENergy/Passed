package com.cenergy.passed_backend.domain.roadmap.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MilestoneCompletionRequest(
        @NotNull Boolean completed,
        @PositiveOrZero Integer studiedMinutes,
        @Size(max = 2000) String note
) {
}

package com.cenergy.passed_backend.domain.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record RecommendationPrepareRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long industryId,
        @NotNull List<@NotNull @Positive Long> jobRoleIds
) {
}

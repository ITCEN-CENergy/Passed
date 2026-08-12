package com.cenergy.passed_backend.domain.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record RecommendationCreateOneRequest(
        @NotNull @Positive Long jobPostingId
) {
}

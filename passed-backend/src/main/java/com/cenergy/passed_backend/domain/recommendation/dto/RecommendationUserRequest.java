package com.cenergy.passed_backend.domain.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecommendationUserRequest(@NotNull @Positive Long userId) {
}

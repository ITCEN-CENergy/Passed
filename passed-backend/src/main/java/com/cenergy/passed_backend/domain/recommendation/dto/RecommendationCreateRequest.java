package com.cenergy.passed_backend.domain.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecommendationCreateRequest(
        @NotNull @Positive Long industryId,
        @NotNull @Size(max = 3) List<@NotNull @Positive Long> jobRoleIds
) {
    public RecommendationCreateRequest {
        jobRoleIds = jobRoleIds == null ? null : List.copyOf(jobRoleIds);
    }
}

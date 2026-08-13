package com.cenergy.passed_backend.domain.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecommendationHistoryRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public RecommendationHistoryRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}

package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecommendationHistoryRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        RecommendationRunType type,
        RecommendationRunStatus status
) {
    public RecommendationHistoryRequest(Integer page, Integer size) {
        this(page, size, null, null);
    }

    public RecommendationHistoryRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}

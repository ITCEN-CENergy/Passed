package com.cenergy.passed_backend.domain.recommendation.dto;

import java.util.List;

public record RecommendationResultResponse(
        RecommendationRunResponse run,
        List<RecommendationItemResponse> recommendations
) {
    public RecommendationResultResponse {
        recommendations = List.copyOf(recommendations);
    }
}

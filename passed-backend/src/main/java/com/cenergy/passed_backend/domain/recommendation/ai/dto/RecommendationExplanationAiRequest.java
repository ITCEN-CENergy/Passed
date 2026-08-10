package com.cenergy.passed_backend.domain.recommendation.ai.dto;

import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanationInput;

import java.util.List;

public record RecommendationExplanationAiRequest(
        List<RecommendationExplanationInput> recommendations
) {
    public RecommendationExplanationAiRequest {
        recommendations = List.copyOf(recommendations);
    }
}

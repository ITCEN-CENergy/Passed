package com.cenergy.passed_backend.domain.recommendation.ai.dto;

import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;

import java.util.List;

public record RecommendationExplanationAiResponse(
        List<RecommendationExplanation> recommendations
) {
}

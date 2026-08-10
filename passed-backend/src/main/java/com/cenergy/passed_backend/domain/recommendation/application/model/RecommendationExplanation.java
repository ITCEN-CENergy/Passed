package com.cenergy.passed_backend.domain.recommendation.application.model;

public record RecommendationExplanation(
        Long jobPostingId,
        String reason,
        String strengths,
        String weaknesses
) {
}

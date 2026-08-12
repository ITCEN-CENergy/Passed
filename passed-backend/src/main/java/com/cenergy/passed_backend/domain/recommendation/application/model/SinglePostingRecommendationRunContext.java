package com.cenergy.passed_backend.domain.recommendation.application.model;

public record SinglePostingRecommendationRunContext(
        RecommendationRunContext run,
        Long jobPostingId
) {
}
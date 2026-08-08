package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.util.Objects;

public record RankedRecommendation(GradedRecommendation recommendation, int rankOrder) {
    public RankedRecommendation {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        if (rankOrder <= 0) {
            throw new IllegalArgumentException("rankOrder must be positive");
        }
    }

    public Long jobPostingId() {
        return recommendation.score().jobPostingId();
    }
}

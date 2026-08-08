package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;

import java.util.Objects;

public record GradedRecommendation(
        RecommendationScoreResult score,
        RecommendationGrade grade,
        int gradePriority
) {
    public GradedRecommendation {
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(grade, "grade must not be null");
        if (gradePriority <= 0) {
            throw new IllegalArgumentException("gradePriority must be positive");
        }
    }
}

package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.util.Objects;

public record RecommendationPostingSummary(
        Long jobPostingId,
        String title,
        String companyName
) {
    public RecommendationPostingSummary {
        Objects.requireNonNull(jobPostingId, "jobPostingId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(companyName, "companyName must not be null");
    }
}

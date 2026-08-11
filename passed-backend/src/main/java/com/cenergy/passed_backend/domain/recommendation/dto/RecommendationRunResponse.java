package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;

import java.time.OffsetDateTime;

public record RecommendationRunResponse(
        Long runId,
        RecommendationRunStatus status,
        RecommendationPreferenceResponse preference,
        RecommendationMetricsResponse metrics,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}

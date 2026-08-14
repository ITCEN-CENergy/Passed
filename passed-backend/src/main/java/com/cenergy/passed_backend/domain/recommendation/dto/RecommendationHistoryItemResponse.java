package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunType;

import java.time.OffsetDateTime;

public record RecommendationHistoryItemResponse(
        Long runId,
        RecommendationRunType recommendationType,
        RecommendationRunStatus status,
        RecommendationPreferenceResponse preference,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}

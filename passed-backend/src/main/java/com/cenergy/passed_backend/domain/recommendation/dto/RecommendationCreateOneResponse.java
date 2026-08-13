package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;

import java.time.OffsetDateTime;

public record RecommendationCreateOneResponse(
        Long runId,
        RecommendationRunStatus status,
        OffsetDateTime startedAt
) {
}

package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationCreateResponse(
        Long runId,
        RecommendationRunStatus status,
        int candidatePostingCount,
        int requiredQualifiedPostingCount,
        Long industryId,
        List<Long> jobRoleIds,
        OffsetDateTime startedAt
) {
    public RecommendationCreateResponse {
        jobRoleIds = List.copyOf(jobRoleIds);
    }
}

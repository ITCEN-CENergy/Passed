package com.cenergy.passed_backend.domain.recommendation.dto;

public record RecommendationDetailResponse(
        Long runId,
        Long jobRecommendationId,
        int rankOrder,
        JobPostingDetailResponse jobPosting,
        RecommendationReportResponse report
) {
}

package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;

public record RecommendationDetailResponse(
        Long runId,
        Long jobRecommendationId,
        int rankOrder,
        JobPostingDetailResponse jobPosting,
        RecommendationReportResponse report
) {
}

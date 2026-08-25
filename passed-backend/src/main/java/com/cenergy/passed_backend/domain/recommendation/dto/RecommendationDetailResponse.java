package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunType;

public record RecommendationDetailResponse(
        Long runId,
        RecommendationRunType recommendationType,
        Long jobRecommendationId,
        int rankOrder,
        JobPostingDetailResponse jobPosting,
        RecommendationReportResponse report
) {
}

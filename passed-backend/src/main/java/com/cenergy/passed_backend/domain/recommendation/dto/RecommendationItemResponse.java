package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;

import java.math.BigDecimal;

public record RecommendationItemResponse(
        Long jobRecommendationId,
        int rankOrder,
        RecommendationGrade grade,
        BigDecimal totalScore,
        String reason,
        JobPostingSummaryResponse jobPosting
) {
}

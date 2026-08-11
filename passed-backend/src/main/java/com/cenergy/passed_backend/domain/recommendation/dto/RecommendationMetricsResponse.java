package com.cenergy.passed_backend.domain.recommendation.dto;

public record RecommendationMetricsResponse(
        int userSkillCount,
        int importantSkillCount,
        Integer candidatePostingCount,
        Integer requiredQualifiedPostingCount
) {
}

package com.cenergy.passed_backend.domain.recommendation.ai.dto;

import java.util.List;

public record RecommendationSkillVerificationAiRequest(
        Long userId,
        List<Long> targetSkillIds
) {
    public RecommendationSkillVerificationAiRequest {
        targetSkillIds = List.copyOf(targetSkillIds);
    }
}

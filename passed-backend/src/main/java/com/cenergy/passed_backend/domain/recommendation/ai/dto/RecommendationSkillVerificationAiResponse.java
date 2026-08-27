package com.cenergy.passed_backend.domain.recommendation.ai.dto;

import com.cenergy.passed_backend.domain.recommendation.application.model.VerifiedSkillMatch;

import java.util.List;

public record RecommendationSkillVerificationAiResponse(
        List<VerifiedSkillMatch> verifiedSkills
) {
}

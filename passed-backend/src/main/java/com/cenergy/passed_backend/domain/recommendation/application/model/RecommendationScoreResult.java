package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RecommendationScoreResult(
        Long jobPostingId,
        BigDecimal totalScore,
        BigDecimal requiredScore,
        BigDecimal preferredScore,
        BigDecimal relatedScore,
        BigDecimal importantSkillBonus,
        int requiredSkillCount,
        int requiredOwnedCount,
        BigDecimal requiredCoverageRate,
        BigDecimal requiredLevelMatchRate,
        int importantSkillCount,
        int importantMatchCount,
        RecommendationCandidateTier candidateTier,
        List<EvaluatedSkillDetail> skillDetails
) {
    public RecommendationScoreResult {
        Objects.requireNonNull(jobPostingId, "jobPostingId must not be null");
        Objects.requireNonNull(totalScore, "totalScore must not be null");
        Objects.requireNonNull(requiredScore, "requiredScore must not be null");
        Objects.requireNonNull(preferredScore, "preferredScore must not be null");
        Objects.requireNonNull(relatedScore, "relatedScore must not be null");
        Objects.requireNonNull(importantSkillBonus, "importantSkillBonus must not be null");
        Objects.requireNonNull(requiredCoverageRate, "requiredCoverageRate must not be null");
        Objects.requireNonNull(
                requiredLevelMatchRate,
                "requiredLevelMatchRate must not be null"
        );
        Objects.requireNonNull(candidateTier, "candidateTier must not be null");
        skillDetails = List.copyOf(Objects.requireNonNull(skillDetails, "skillDetails must not be null"));
    }
}

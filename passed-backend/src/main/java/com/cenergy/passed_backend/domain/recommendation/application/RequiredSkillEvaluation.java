package com.cenergy.passed_backend.domain.recommendation.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RequiredSkillEvaluation(
        int requiredSkillCount,
        int requiredOwnedCount,
        BigDecimal requiredCoverageRate,
        BigDecimal requiredLevelMatchRate,
        BigDecimal requiredScore,
        List<RequiredSkillMatch> skillMatches
) {
    public RequiredSkillEvaluation {
        requiredCoverageRate = Objects.requireNonNull(
                requiredCoverageRate,
                "requiredCoverageRate must not be null"
        );
        requiredLevelMatchRate = Objects.requireNonNull(
                requiredLevelMatchRate,
                "requiredLevelMatchRate must not be null"
        );
        requiredScore = Objects.requireNonNull(requiredScore, "requiredScore must not be null");
        skillMatches = List.copyOf(Objects.requireNonNull(skillMatches, "skillMatches must not be null"));
    }

    public record RequiredSkillMatch(
            Long skillId,
            short requiredLevel,
            Short userLevel,
            boolean owned,
            boolean requirementSatisfied,
            BigDecimal matchRate
    ) {
        public RequiredSkillMatch {
            matchRate = Objects.requireNonNull(matchRate, "matchRate must not be null");
        }
    }
}

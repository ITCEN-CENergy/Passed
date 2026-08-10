package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;

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
            SkillEvaluationType evaluationType,
            boolean owned,
            boolean requirementSatisfied,
            BigDecimal matchRate
    ) {
        public RequiredSkillMatch {
            evaluationType = Objects.requireNonNull(
                    evaluationType,
                    "evaluationType must not be null"
            );
            matchRate = Objects.requireNonNull(matchRate, "matchRate must not be null");
        }
    }
}

package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;

import java.math.BigDecimal;
import java.util.Objects;

public record EvaluatedSkillDetail(
        Long skillId,
        String skillName,
        JobPostingSkillType skillType,
        short requiredLevel,
        Short userLevel,
        SkillEvaluationType evaluationType,
        boolean owned,
        boolean requirementSatisfied,
        boolean userImportant,
        BigDecimal matchRate,
        BigDecimal baseMaxScore,
        BigDecimal baseContributionScore,
        BigDecimal importantBonusContributionScore
) {
    public EvaluatedSkillDetail {
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(skillType, "skillType must not be null");
        Objects.requireNonNull(evaluationType, "evaluationType must not be null");
        Objects.requireNonNull(matchRate, "matchRate must not be null");
        Objects.requireNonNull(baseMaxScore, "baseMaxScore must not be null");
        Objects.requireNonNull(baseContributionScore, "baseContributionScore must not be null");
        Objects.requireNonNull(
                importantBonusContributionScore,
                "importantBonusContributionScore must not be null"
        );
    }
}

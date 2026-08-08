package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.util.List;

public record RecommendationExplanationInput(
        Long jobPostingId,
        String jobPostingTitle,
        String companyName,
        int rankOrder,
        String recommendationGrade,
        String candidateTier,
        String totalScore,
        String requiredScore,
        String preferredScore,
        String relatedScore,
        String importantSkillBonus,
        String requiredCoverageRate,
        String requiredLevelMatchRate,
        int importantMatchCount,
        List<SkillFact> strengths,
        List<SkillFact> gaps
) {
    public RecommendationExplanationInput {
        strengths = List.copyOf(strengths);
        gaps = List.copyOf(gaps);
    }

    public record SkillFact(
            String skillName,
            String skillType,
            String evaluationType,
            Short userLevel,
            short requiredLevel,
            String matchRate,
            boolean userImportant,
            boolean requirementSatisfied
    ) {
    }
}

package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.util.List;

public record RecommendationExplanationInput(
        Long jobPostingId,
        String jobPostingTitle,
        String companyName,
        JobPostingContext posting,
        List<SkillFact> matchedSkills,
        List<SkillFact> gapSkills
) {
    public RecommendationExplanationInput {
        matchedSkills = List.copyOf(matchedSkills);
        gapSkills = List.copyOf(gapSkills);
    }

    public record JobPostingContext(
            String positionDetail,
            String mainDuty,
            String qualification,
            String preference,
            String companyTalentProfile
    ) {
    }

    public record SkillFact(
            String skillName,
            String skillType,
            Short userLevel,
            short requiredLevel,
            String matchRate,
            boolean requirementSatisfied
    ) {
    }
}
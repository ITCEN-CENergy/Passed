package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationReportResponse(
        RecommendationGrade grade,
        BigDecimal totalScore,
        String reason,
        List<SkillGroupResponse> skillGroups,
        List<HighlightedSkillResponse> topStrengthSkills,
        List<HighlightedSkillResponse> topGapSkills
) {
    public RecommendationReportResponse {
        skillGroups = List.copyOf(skillGroups);
        topStrengthSkills = List.copyOf(topStrengthSkills);
        topGapSkills = List.copyOf(topGapSkills);
    }
}

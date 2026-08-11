package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;

import java.math.BigDecimal;
import java.util.List;

public record SkillGroupResponse(
        JobPostingSkillType skillType,
        BigDecimal levelMatchRate,
        int ownedCount,
        int totalCount,
        List<SkillMatchResponse> skills
) {
    public SkillGroupResponse {
        skills = List.copyOf(skills);
    }
}

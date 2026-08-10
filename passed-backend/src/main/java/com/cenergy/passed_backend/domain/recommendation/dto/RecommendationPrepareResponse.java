package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;

import java.util.List;

public record RecommendationPrepareResponse(
        Long recommendationRunId,
        RecommendationRunStatus status,
        String policyCode,
        String policyVersion,
        int gradeRuleCount,
        int userSkillCount,
        int importantSkillCount,
        int candidatePostingCount,
        int requiredQualifiedPostingCount,
        String userSkillSnapshotHash,
        Long industryId,
        List<Long> jobRoleIds
) {
    public RecommendationPrepareResponse {
        jobRoleIds = List.copyOf(jobRoleIds);
    }
}

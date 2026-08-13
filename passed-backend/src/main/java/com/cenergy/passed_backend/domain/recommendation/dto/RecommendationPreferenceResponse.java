package com.cenergy.passed_backend.domain.recommendation.dto;

import com.cenergy.passed_backend.domain.user.dto.JobRoleResponse;

import java.util.List;

public record RecommendationPreferenceResponse(
        Long industryId,
        String industryName,
        List<JobRoleResponse> jobRoles
) {
    public RecommendationPreferenceResponse {
        jobRoles = List.copyOf(jobRoles);
    }
}

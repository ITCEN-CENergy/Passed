package com.cenergy.passed_backend.domain.user.dto;

import java.util.List;

public record JobRoleListResponse(
        IndustryResponse industry,
        List<JobRoleResponse> jobRoles
) {
    public JobRoleListResponse {
        jobRoles = List.copyOf(jobRoles);
    }
}

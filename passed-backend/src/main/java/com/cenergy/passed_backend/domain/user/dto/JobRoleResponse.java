package com.cenergy.passed_backend.domain.user.dto;

import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;

public record JobRoleResponse(Long id, String name) {
    public static JobRoleResponse from(JobRole jobRole) {
        return new JobRoleResponse(jobRole.getId(), jobRole.getJobRoleName());
    }
}

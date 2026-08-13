package com.cenergy.passed_backend.domain.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record UserJobPreferenceResponse(
        Long userId,
        IndustryResponse industry,
        List<JobRoleResponse> desiredJobs,
        OffsetDateTime updatedAt
) {
    public UserJobPreferenceResponse {
        desiredJobs = List.copyOf(desiredJobs);
    }
}

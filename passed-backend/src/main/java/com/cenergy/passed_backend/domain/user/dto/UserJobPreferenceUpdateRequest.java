package com.cenergy.passed_backend.domain.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record UserJobPreferenceUpdateRequest(
        @NotNull @Positive Long industryId,
        @NotEmpty List<@NotNull @Positive Long> jobRoleIds
) {
}

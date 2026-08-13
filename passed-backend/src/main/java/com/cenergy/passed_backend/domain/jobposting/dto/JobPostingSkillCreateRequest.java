package com.cenergy.passed_backend.domain.jobposting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record JobPostingSkillCreateRequest(
        @NotNull @Positive Long skillId,
        @Min(1) @Max(3) short skillLevel
) {
}

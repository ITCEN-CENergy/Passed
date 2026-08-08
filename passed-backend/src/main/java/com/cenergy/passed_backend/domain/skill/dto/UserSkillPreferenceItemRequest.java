package com.cenergy.passed_backend.domain.skill.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserSkillPreferenceItemRequest(
        @NotNull Long userSkillId,
        @NotNull @Min(1) @Max(3) Integer level,
        @NotNull Boolean isImportantForMatching
) {
}

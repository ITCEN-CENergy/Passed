package com.cenergy.passed_backend.domain.skill.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserSkillPreferenceUpdateRequest(
        @NotNull List<@Valid UserSkillPreferenceItemRequest> skills
) {
}

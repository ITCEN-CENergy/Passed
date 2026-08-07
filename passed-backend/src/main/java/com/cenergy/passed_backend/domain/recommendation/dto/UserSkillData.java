package com.cenergy.passed_backend.domain.recommendation.dto;

public record UserSkillData(
        Long skillId,
        short skillLevel,
        boolean important
) {
}

package com.cenergy.passed_backend.domain.recommendation.dto;

public record UserSkillSnapshotResponse(
        Long skillId,
        String skillName,
        String category,
        short skillLevel,
        boolean isImportant
) {
}

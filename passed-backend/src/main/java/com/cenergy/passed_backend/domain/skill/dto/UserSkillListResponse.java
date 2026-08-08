package com.cenergy.passed_backend.domain.skill.dto;

import java.util.List;

public record UserSkillListResponse(
        int totalSkillCount,
        int maxImportantCount,
        boolean preferenceEditable,
        List<UserSkillResponse> skills
) {
}

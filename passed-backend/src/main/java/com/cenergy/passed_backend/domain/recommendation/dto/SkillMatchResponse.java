package com.cenergy.passed_backend.domain.recommendation.dto;

import java.math.BigDecimal;

public record SkillMatchResponse(
        Long skillId,
        String skillName,
        boolean owned,
        boolean isImportant,
        BigDecimal matchRate
) {
}

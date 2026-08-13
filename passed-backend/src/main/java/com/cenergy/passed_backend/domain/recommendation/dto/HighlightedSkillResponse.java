package com.cenergy.passed_backend.domain.recommendation.dto;

import java.math.BigDecimal;

public record HighlightedSkillResponse(
        Long skillId,
        String skillName,
        boolean isImportant,
        BigDecimal matchRate
    ) {
}

package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.math.BigDecimal;

public record VerifiedSkillMatch(
        Long targetSkillId,
        String targetSkillName,
        Long sourceSkillId,
        String sourceSkillName,
        short inferredLevel,
        String evidence,
        BigDecimal similarity,
        String relationship
) {
}

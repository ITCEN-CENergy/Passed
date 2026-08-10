package com.cenergy.passed_backend.domain.skill.dto;

import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;

import java.math.BigDecimal;

public record UserSkillResponse(
        Long userSkillId,
        Long skillId,
        String name,
        SkillCategory category,
        short level,
        boolean isImportantForMatching,
        BigDecimal mappingConfidence,
        BigDecimal levelConfidence
) {
    public static UserSkillResponse from(UserSkill userSkill) {
        return new UserSkillResponse(
                userSkill.getId(),
                userSkill.getSkill().getId(),
                userSkill.getSkill().getName(),
                userSkill.getSkill().getCategory(),
                userSkill.getSkillLevel(),
                userSkill.isImportant(),
                userSkill.getMappingConfidence(),
                userSkill.getLevelConfidence()
        );
    }
}

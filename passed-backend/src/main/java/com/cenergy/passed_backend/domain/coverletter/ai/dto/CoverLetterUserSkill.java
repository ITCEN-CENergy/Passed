package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverLetterUserSkill(
        @JsonProperty("skill_id") Long skillId,
        String name,
        SkillCategory category,
        short level
) {
    public static CoverLetterUserSkill from(UserSkill userSkill) {
        return new CoverLetterUserSkill(
                userSkill.getSkill().getId(),
                userSkill.getSkill().getName(),
                userSkill.getSkill().getCategory(),
                userSkill.getSkillLevel()
        );
    }
}

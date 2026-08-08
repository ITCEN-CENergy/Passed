package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;

import java.util.List;
import java.util.Objects;

public record PostingSkillBundle(
        List<PostingSkill> requiredSkills,
        List<PostingSkill> preferredSkills,
        List<PostingSkill> relatedSkills
) {
    public PostingSkillBundle {
        requiredSkills = List.copyOf(
                Objects.requireNonNull(requiredSkills, "requiredSkills must not be null")
        );
        preferredSkills = List.copyOf(
                Objects.requireNonNull(preferredSkills, "preferredSkills must not be null")
        );
        relatedSkills = List.copyOf(
                Objects.requireNonNull(relatedSkills, "relatedSkills must not be null")
        );
    }

    public static PostingSkillBundle empty() {
        return new PostingSkillBundle(List.of(), List.of(), List.of());
    }

    public int requiredSkillCount() {
        return requiredSkills.size();
    }

    public int preferredSkillCount() {
        return preferredSkills.size();
    }

    public int relatedSkillCount() {
        return relatedSkills.size();
    }

    public record PostingSkill(
            Long skillId,
            String skillName,
            SkillCategory skillCategory,
            short requiredLevel
    ) {
        public PostingSkill {
            if (skillId == null || skillId <= 0) {
                throw new IllegalArgumentException("skillId must be positive");
            }
            if (skillName == null || skillName.isBlank()) {
                throw new IllegalArgumentException("skillName must not be blank");
            }
            Objects.requireNonNull(skillCategory, "skillCategory must not be null");
            if (requiredLevel < 1 || requiredLevel > 3) {
                throw new IllegalArgumentException("requiredLevel must be between 1 and 3");
            }
        }
    }
}

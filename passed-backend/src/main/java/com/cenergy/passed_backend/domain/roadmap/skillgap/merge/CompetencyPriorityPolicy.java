package com.cenergy.passed_backend.domain.roadmap.skillgap.merge;

import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;

@Component
public class CompetencyPriorityPolicy {

    public int calculateScore(RequirementType requirementType, int gapLevel, int frequency) {
        return weightOf(requirementType) * 100 + gapLevel * 10 + frequency;
    }

    public RequirementType strongestOf(Collection<RequirementType> requirementTypes) {
        if (requirementTypes == null || requirementTypes.isEmpty()) {
            throw invalid("requirementTypes must not be empty");
        }
        return requirementTypes.stream()
                .max(Comparator.comparingInt(this::weightOf))
                .orElseThrow(() -> invalid("requirementTypes must not be empty"));
    }

    public int weightOf(RequirementType requirementType) {
        if (requirementType == null) {
            throw invalid("requirementType must not be null");
        }
        return switch (requirementType) {
            case REQUIRED -> 3;
            case PREFERRED -> 2;
            case RELATED -> 1;
        };
    }

    private SkillGapException invalid(String message) {
        return new SkillGapException(ErrorCode.SKILL_GAP_INVALID_RESPONSE, message);
    }
}

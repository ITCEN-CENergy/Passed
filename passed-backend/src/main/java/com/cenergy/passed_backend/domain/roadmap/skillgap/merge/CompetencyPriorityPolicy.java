package com.cenergy.passed_backend.domain.roadmap.skillgap.merge;

import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class CompetencyPriorityPolicy {

    public int calculateScore(RequirementType requirementType, int gapLevel, int frequency) {
        return weightOf(requirementType) * 100 + gapLevel * 10 + frequency;
    }

    public RequirementType resolveForRoadmap(
            Collection<RequirementType> requirementTypes,
            int competencyPostingCount,
            int selectedPostingCount
    ) {
        if (requirementTypes == null || requirementTypes.isEmpty()) {
            throw invalid("requirementTypes must not be empty");
        }
        if (requirementTypes.stream().anyMatch(type -> type == null)) {
            throw invalid("requirementTypes must not contain null");
        }
        if (competencyPostingCount <= 0 || selectedPostingCount <= 0
                || competencyPostingCount > selectedPostingCount) {
            throw invalid("invalid posting counts");
        }
        boolean requiredOrPreferredInEveryPosting = competencyPostingCount == selectedPostingCount
                && requirementTypes.stream().allMatch(type ->
                        type == RequirementType.REQUIRED || type == RequirementType.PREFERRED);
        if (requiredOrPreferredInEveryPosting) {
            return RequirementType.REQUIRED;
        }
        boolean hasRequiredOrPreferred = requirementTypes.stream()
                .anyMatch(type -> type == RequirementType.REQUIRED || type == RequirementType.PREFERRED);
        return hasRequiredOrPreferred ? RequirementType.PREFERRED : RequirementType.RELATED;
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

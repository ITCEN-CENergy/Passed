package com.cenergy.passed_backend.domain.roadmap.skillgap.validation;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyItem;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class LearningCompetencyResponseValidator {

    public ValidatedSkillGapResult validate(Long requestedUserId, Long requestedJobPostingId,
                                            LearningCompetencyResponse response) {
        requirePositive(requestedUserId, "requested userId");
        requirePositive(requestedJobPostingId, "requested jobPostingId");
        invalidIf(response == null, "response must not be null");
        requirePositive(response.userId(), "userId");
        requirePositive(response.jobPostingId(), "jobPostingId");
        invalidIf(!requestedUserId.equals(response.userId()), "response userId does not match request");
        invalidIf(!requestedJobPostingId.equals(response.jobPostingId()), "response jobPostingId does not match request");
        invalidIf(response.competencies() == null, "competencies must not be null");

        Set<Long> competencyIds = new HashSet<>();
        List<ValidatedCompetencyGap> validatedCompetencies = response.competencies().stream()
                .map(gap -> validateCompetency(gap, competencyIds))
                .toList();
        return new ValidatedSkillGapResult(response.userId(), response.jobPostingId(), validatedCompetencies);
    }

    private ValidatedCompetencyGap validateCompetency(
            LearningCompetencyItem gap, Set<Long> competencyIds
    ) {
        invalidIf(gap == null, "competency gap item must not be null");
        requirePositive(gap.standardCompetencyId(), "standardCompetencyId");
        invalidIf(gap.standardCompetencyName() == null || gap.standardCompetencyName().isBlank(),
                "standardCompetencyName must not be blank");
        invalidIf(gap.category() == null, "category must not be null");
        invalidIf(gap.requirementType() == null, "requirementType must not be null");
        requireNonNegative(gap.currentLevel(), "currentLevel");
        requireNonNegative(gap.targetLevel(), "targetLevel");
        invalidIf(!competencyIds.add(gap.standardCompetencyId()), "duplicate standardCompetencyId");

        int calculatedGapLevel = Math.max(gap.targetLevel() - gap.currentLevel(), 0);
        validateCertification(gap, calculatedGapLevel);
        validateGeneralCompetency(gap);

        return new ValidatedCompetencyGap(
                gap.standardCompetencyId(), gap.standardCompetencyName(), gap.category(), gap.requirementType(),
                gap.currentLevel(), gap.targetLevel(), calculatedGapLevel, gap.currentLevelEvidence());
    }

    private void validateCertification(LearningCompetencyItem gap, int calculatedGapLevel) {
        if (gap.category() != CompetencyCategory.CERTIFICATION) {
            return;
        }
        invalidIf((gap.currentLevel() != 0 && gap.currentLevel() != 1)
                        || gap.targetLevel() != 1
                        || (calculatedGapLevel != 0 && calculatedGapLevel != 1),
                "invalid CERTIFICATION levels");
    }

    private void validateGeneralCompetency(LearningCompetencyItem gap) {
        if (gap.category() == CompetencyCategory.CERTIFICATION) {
            return;
        }
        invalidIf(gap.currentLevel() < 1 || gap.currentLevel() > 3
                        || gap.targetLevel() < 1 || gap.targetLevel() > 3,
                "non-certification levels must be between 1 and 3");
    }

    private void requirePositive(Long value, String field) {
        invalidIf(value == null || value <= 0, field + " must be positive");
    }

    private void requireNonNegative(Integer value, String field) {
        invalidIf(value == null || value < 0, field + " must be non-negative");
    }

    private void invalidIf(boolean invalid, String message) {
        if (invalid) {
            throw new SkillGapException(ErrorCode.SKILL_GAP_INVALID_RESPONSE, message);
        }
    }
}

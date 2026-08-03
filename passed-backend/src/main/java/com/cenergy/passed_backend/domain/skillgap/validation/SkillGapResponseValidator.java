package com.cenergy.passed_backend.domain.skillgap.validation;

import com.cenergy.passed_backend.domain.skillgap.dto.CompetencyGapResponse;
import com.cenergy.passed_backend.domain.skillgap.dto.SkillGapResponse;
import com.cenergy.passed_backend.domain.skillgap.model.ValidatedCompetencyGap;
import com.cenergy.passed_backend.domain.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SkillGapResponseValidator {
    private static final Logger log = LoggerFactory.getLogger(SkillGapResponseValidator.class);

    public ValidatedSkillGapResult validate(Long requestedUserId, Long requestedJobPostingId, SkillGapResponse response) {
        requirePositive(requestedUserId, "requested userId");
        requirePositive(requestedJobPostingId, "requested jobPostingId");
        invalidIf(response == null, "response must not be null");
        requirePositive(response.userId(), "userId");
        requirePositive(response.jobPostingId(), "jobPostingId");
        invalidIf(!requestedUserId.equals(response.userId()), "response userId does not match request");
        invalidIf(!requestedJobPostingId.equals(response.jobPostingId()), "response jobPostingId does not match request");
        invalidIf(response.competencyGaps() == null, "competencyGaps must not be null");

        Set<Long> competencyIds = new HashSet<>();
        List<ValidatedCompetencyGap> validatedGaps = response.competencyGaps().stream()
                .map(gap -> validateGap(response.userId(), response.jobPostingId(), gap, competencyIds))
                .toList();
        return new ValidatedSkillGapResult(response.userId(), response.jobPostingId(), validatedGaps);
    }

    private ValidatedCompetencyGap validateGap(
            Long userId, Long jobPostingId, CompetencyGapResponse gap, Set<Long> competencyIds
    ) {
        invalidIf(gap == null, "competency gap item must not be null");
        requirePositive(gap.standardCompetencyId(), "standardCompetencyId");
        invalidIf(gap.standardCompetencyName() == null || gap.standardCompetencyName().isBlank(),
                "standardCompetencyName must not be blank");
        invalidIf(gap.category() == null, "category must not be null");
        invalidIf(gap.requirementType() == null, "requirementType must not be null");
        requireNonNegative(gap.currentLevel(), "currentLevel");
        requireNonNegative(gap.targetLevel(), "targetLevel");
        if (gap.gapLevel() != null) {
            requireNonNegative(gap.gapLevel(), "gapLevel");
        }
        invalidIf(!competencyIds.add(gap.standardCompetencyId()), "duplicate standardCompetencyId");

        int calculatedGapLevel = Math.max(gap.targetLevel() - gap.currentLevel(), 0);
        if (gap.gapLevel() != null && gap.gapLevel() != calculatedGapLevel) {
            log.warn("Skill gap mismatch: userId={}, jobPostingId={}, standardCompetencyId={}, externalGapLevel={}, calculatedGapLevel={}",
                    userId, jobPostingId, gap.standardCompetencyId(), gap.gapLevel(), calculatedGapLevel);
        }
        validateCertification(gap, calculatedGapLevel);

        return new ValidatedCompetencyGap(
                gap.standardCompetencyId(), gap.standardCompetencyName(), gap.category(), gap.requirementType(),
                gap.currentLevel(), gap.targetLevel(), calculatedGapLevel, gap.currentEvidence());
    }

    private void validateCertification(CompetencyGapResponse gap, int calculatedGapLevel) {
        if (gap.category() != CompetencyCategory.CERTIFICATION) {
            return;
        }
        invalidIf((gap.currentLevel() != 0 && gap.currentLevel() != 1)
                        || gap.targetLevel() != 1
                        || (calculatedGapLevel != 0 && calculatedGapLevel != 1),
                "invalid CERTIFICATION levels");
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

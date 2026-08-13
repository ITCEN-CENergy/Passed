package com.cenergy.passed_backend.domain.roadmap.skillgap.validation;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyItem;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
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
public class LearningCompetencyResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(LearningCompetencyResponseValidator.class);

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

        log.info("Validating learning competencies: requestedUserId={}, requestedJobPostingId={}, "
                        + "responseUserId={}, responseJobPostingId={}, count={}",
                requestedUserId, requestedJobPostingId, response.userId(), response.jobPostingId(),
                response.competencies().size());

        Set<Long> competencyIds = new HashSet<>();
        List<ValidatedCompetencyGap> validatedCompetencies = response.competencies().stream().map(gap -> {
            try {
                ValidatedCompetencyGap validated = validateCompetency(gap, competencyIds);
                log.info("Learning competency validation passed: jobPostingId={}, competencyId={}, "
                                + "category={}, currentLevel={}, targetLevel={}, calculatedGapLevel={}",
                        requestedJobPostingId, validated.standardCompetencyId(), validated.category(),
                        validated.currentLevel(), validated.targetLevel(), validated.gapLevel());
                return validated;
            } catch (SkillGapException exception) {
                log.error("Learning competency validation failed: jobPostingId={}, item={}, reason={}",
                        requestedJobPostingId, summarize(gap), exception.getMessage());
                throw exception;
            }
        }).toList();
        return new ValidatedSkillGapResult(response.userId(), response.jobPostingId(), validatedCompetencies);
    }

    private String summarize(LearningCompetencyItem item) {
        if (item == null) {
            return "null";
        }
        return "competencyId=" + item.standardCompetencyId()
                + ", name='" + item.standardCompetencyName() + '\''
                + ", category=" + item.category()
                + ", requirementType=" + item.requirementType()
                + ", currentLevel=" + item.currentLevel()
                + ", targetLevel=" + item.targetLevel()
                + ", hasEvidence=" + (item.currentLevelEvidence() != null
                && !item.currentLevelEvidence().isBlank());
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
        requireNonNegative(gap.targetLevel(), "targetLevel");
        invalidIf(!competencyIds.add(gap.standardCompetencyId()), "duplicate standardCompetencyId");

        int currentLevel = gap.currentLevel() == null ? 0 : gap.currentLevel();
        requireNonNegative(currentLevel, "currentLevel");
        int calculatedGapLevel = Math.max(gap.targetLevel() - currentLevel, 0);
        validateCertification(gap, currentLevel, calculatedGapLevel);
        validateGeneralCompetency(gap, currentLevel);

        return new ValidatedCompetencyGap(
                gap.standardCompetencyId(), gap.standardCompetencyName(), gap.category(), gap.requirementType(),
                currentLevel, gap.targetLevel(), calculatedGapLevel, gap.currentLevelEvidence());
    }

    private void validateCertification(LearningCompetencyItem gap, int currentLevel, int calculatedGapLevel) {
        if (gap.category() != CompetencyCategory.CERTIFICATION) {
            return;
        }
        invalidIf((currentLevel != 0 && currentLevel != 1)
                        || gap.targetLevel() != 1
                        || (calculatedGapLevel != 0 && calculatedGapLevel != 1),
                "invalid CERTIFICATION levels");
    }

    private void validateGeneralCompetency(LearningCompetencyItem gap, int currentLevel) {
        if (gap.category() == CompetencyCategory.CERTIFICATION) {
            return;
        }
        invalidIf(currentLevel < 0 || currentLevel > 3
                        || gap.targetLevel() < 1 || gap.targetLevel() > 3,
                "non-certification currentLevel must be between 0 and 3 and targetLevel between 1 and 3");
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

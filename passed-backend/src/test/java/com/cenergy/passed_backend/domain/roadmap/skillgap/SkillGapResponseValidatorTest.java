package com.cenergy.passed_backend.domain.roadmap.skillgap;

import com.cenergy.passed_backend.domain.skillgap.dto.CompetencyGapResponse;
import com.cenergy.passed_backend.domain.skillgap.dto.SkillGapResponse;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.roadmap.skillgap.validation.SkillGapResponseValidator;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillGapResponseValidatorTest {
    private final SkillGapResponseValidator validator = new SkillGapResponseValidator();

    @Test
    void validatesNormalResponseAndSeparatesInternalModel() {
        ValidatedSkillGapResult result = validator.validate(10L, 101L, response(gap()));

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.jobPostingId()).isEqualTo(101L);
        assertThat(result.competencyGaps()).singleElement().satisfies(gap -> {
            assertThat(gap.standardCompetencyName()).isEqualTo("Docker");
            assertThat(gap.gapLevel()).isEqualTo(2);
            assertThat(gap.currentEvidence()).isEqualTo("실습 경험");
        });
    }

    @Test
    void allowsEmptyCompetencyGaps() {
        assertThat(validator.validate(10L, 101L, response()).competencyGaps()).isEmpty();
    }

    @Test
    void rejectsNullResponse() {
        assertInvalid(() -> validator.validate(10L, 101L, null));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidResponseUserId(Long value) {
        assertInvalid(() -> validator.validate(10L, 101L, new SkillGapResponse(value, 101L, List.of())));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidResponseJobPostingId(Long value) {
        assertInvalid(() -> validator.validate(10L, 101L, new SkillGapResponse(10L, value, List.of())));
    }

    @Test
    void rejectsMismatchedUserId() {
        assertInvalid(() -> validator.validate(11L, 101L, response()));
    }

    @Test
    void rejectsMismatchedJobPostingId() {
        assertInvalid(() -> validator.validate(10L, 102L, response()));
    }

    @Test
    void rejectsNullCompetencyGaps() {
        assertInvalid(() -> validator.validate(10L, 101L, new SkillGapResponse(10L, 101L, null)));
    }

    @Test
    void rejectsNullItem() {
        assertInvalid(() -> validator.validate(10L, 101L,
                new SkillGapResponse(10L, 101L, java.util.Arrays.asList((CompetencyGapResponse) null))));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidCompetencyId(Long id) {
        assertInvalidGap(new CompetencyGapResponse(id, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, 2, null));
    }

    @ParameterizedTest
    @MethodSource("blankNames")
    void rejectsBlankCompetencyName(String name) {
        assertInvalidGap(new CompetencyGapResponse(1L, name, CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, 2, null));
    }

    @Test
    void rejectsNullCategory() {
        assertInvalidGap(new CompetencyGapResponse(1L, "Docker", null,
                RequirementType.REQUIRED, 1, 3, 2, null));
    }

    @Test
    void rejectsNullRequirementType() {
        assertInvalidGap(new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                null, 1, 3, 2, null));
    }

    @Test
    void rejectsNegativeCurrentLevel() {
        assertInvalidGap(new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, -1, 3, 2, null));
    }

    @Test
    void rejectsNegativeTargetLevel() {
        assertInvalidGap(new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, -1, 2, null));
    }

    @Test
    void rejectsNegativeExternalGapLevel() {
        assertInvalidGap(new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, -1, null));
    }

    @Test
    void rejectsDuplicateCompetencyId() {
        assertInvalid(() -> validator.validate(10L, 101L, response(gap(), gap())));
    }

    @Test
    void recalculatesGapInsteadOfUsingExternalValue() {
        CompetencyGapResponse external = new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, 99, null);
        assertThat(validator.validate(10L, 101L, response(external))
                .competencyGaps().getFirst().gapLevel()).isEqualTo(2);
    }

    @Test
    void calculatesGapWhenExternalValueIsNull() {
        CompetencyGapResponse external = new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, null, null);
        assertThat(validator.validate(10L, 101L, response(external))
                .competencyGaps().getFirst().gapLevel()).isEqualTo(2);
    }

    @Test
    void floorsCalculatedGapAtZero() {
        CompetencyGapResponse external = new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 3, 1, 0, null);
        assertThat(validator.validate(10L, 101L, response(external))
                .competencyGaps().getFirst().gapLevel()).isZero();
    }

    @ParameterizedTest
    @MethodSource("validCertifications")
    void acceptsValidCertificationLevels(CompetencyGapResponse certification) {
        assertThat(validator.validate(10L, 101L, response(certification)).competencyGaps()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("invalidCertifications")
    void rejectsInvalidCertificationLevels(CompetencyGapResponse certification) {
        assertInvalidGap(certification);
    }

    private SkillGapResponse response(CompetencyGapResponse... gaps) {
        return new SkillGapResponse(10L, 101L, List.of(gaps));
    }

    private CompetencyGapResponse gap() {
        return new CompetencyGapResponse(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, 2, "실습 경험");
    }

    private void assertInvalidGap(CompetencyGapResponse gap) {
        assertInvalid(() -> validator.validate(10L, 101L, response(gap)));
    }

    private void assertInvalid(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(SkillGapException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_GAP_INVALID_RESPONSE);
    }

    static Stream<Long> invalidIds() {
        return Stream.of(null, 0L, -1L);
    }

    static Stream<String> blankNames() {
        return Stream.of(null, "", "   ");
    }

    static Stream<CompetencyGapResponse> validCertifications() {
        return Stream.of(certification(0, 1, 1), certification(1, 1, 0));
    }

    static Stream<CompetencyGapResponse> invalidCertifications() {
        return Stream.of(certification(2, 1, 0), certification(0, 2, 2));
    }

    private static CompetencyGapResponse certification(int current, int target, int gap) {
        return new CompetencyGapResponse(10L, "SQLD", CompetencyCategory.CERTIFICATION,
                RequirementType.PREFERRED, current, target, gap, null);
    }
}

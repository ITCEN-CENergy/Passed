package com.cenergy.passed_backend.domain.roadmap.skillgap;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyItem;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.roadmap.skillgap.validation.LearningCompetencyResponseValidator;
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

class LearningCompetencyResponseValidatorTest {
    private final LearningCompetencyResponseValidator validator = new LearningCompetencyResponseValidator();

    @Test
    void validatesNormalResponseAndSeparatesInternalModel() {
        ValidatedSkillGapResult result = validator.validate(10L, 101L, response(gap()));

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.jobPostingId()).isEqualTo(101L);
        assertThat(result.competencies()).singleElement().satisfies(gap -> {
            assertThat(gap.standardCompetencyName()).isEqualTo("Docker");
            assertThat(gap.gapLevel()).isEqualTo(2);
            assertThat(gap.currentEvidence()).isEqualTo("실습 경험");
        });
    }

    @Test
    void allowsEmptyCompetencies() {
        assertThat(validator.validate(10L, 101L, response()).competencies()).isEmpty();
    }

    @Test
    void rejectsNullResponse() {
        assertInvalid(() -> validator.validate(10L, 101L, null));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidResponseUserId(Long value) {
        assertInvalid(() -> validator.validate(10L, 101L, new LearningCompetencyResponse(value, 101L, List.of())));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidResponseJobPostingId(Long value) {
        assertInvalid(() -> validator.validate(10L, 101L, new LearningCompetencyResponse(10L, value, List.of())));
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
    void rejectsNullCompetencies() {
        assertInvalid(() -> validator.validate(10L, 101L, new LearningCompetencyResponse(10L, 101L, null)));
    }

    @Test
    void rejectsNullItem() {
        assertInvalid(() -> validator.validate(10L, 101L,
                new LearningCompetencyResponse(10L, 101L, java.util.Arrays.asList((LearningCompetencyItem) null))));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidCompetencyId(Long id) {
        assertInvalidGap(new LearningCompetencyItem(id, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, null));
    }

    @ParameterizedTest
    @MethodSource("blankNames")
    void rejectsBlankCompetencyName(String name) {
        assertInvalidGap(new LearningCompetencyItem(1L, name, CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, null));
    }

    @Test
    void rejectsNullCategory() {
        assertInvalidGap(new LearningCompetencyItem(1L, "Docker", null,
                RequirementType.REQUIRED, 1, 3, null));
    }

    @Test
    void rejectsNullRequirementType() {
        assertInvalidGap(new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                null, 1, 3, null));
    }

    @Test
    void rejectsNegativeCurrentLevel() {
        assertInvalidGap(new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, -1, 3, null));
    }

    @Test
    void rejectsNegativeTargetLevel() {
        assertInvalidGap(new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, -1, null));
    }

    @Test
    void rejectsDuplicateCompetencyId() {
        assertInvalid(() -> validator.validate(10L, 101L, response(gap(), gap())));
    }

    @Test
    void calculatesGapFromLevels() {
        LearningCompetencyItem external = new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, null);
        assertThat(validator.validate(10L, 101L, response(external))
                .competencies().getFirst().gapLevel()).isEqualTo(2);
    }

    @Test
    void floorsCalculatedGapAtZero() {
        LearningCompetencyItem external = new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 3, 1, null);
        assertThat(validator.validate(10L, 101L, response(external))
                .competencies().getFirst().gapLevel()).isZero();
    }

    @ParameterizedTest
    @MethodSource("validCertifications")
    void acceptsValidCertificationLevels(LearningCompetencyItem certification) {
        assertThat(validator.validate(10L, 101L, response(certification)).competencies()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("invalidCertifications")
    void rejectsInvalidCertificationLevels(LearningCompetencyItem certification) {
        assertInvalidGap(certification);
    }

    private LearningCompetencyResponse response(LearningCompetencyItem... gaps) {
        return new LearningCompetencyResponse(10L, 101L, List.of(gaps));
    }

    private LearningCompetencyItem gap() {
        return new LearningCompetencyItem(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                RequirementType.REQUIRED, 1, 3, "실습 경험");
    }

    private void assertInvalidGap(LearningCompetencyItem gap) {
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

    static Stream<LearningCompetencyItem> validCertifications() {
        return Stream.of(certification(0, 1), certification(1, 1));
    }

    static Stream<LearningCompetencyItem> invalidCertifications() {
        return Stream.of(certification(2, 1), certification(0, 2));
    }

    private static LearningCompetencyItem certification(int current, int target) {
        return new LearningCompetencyItem(10L, "SQLD", CompetencyCategory.CERTIFICATION,
                RequirementType.PREFERRED, current, target, null);
    }
}

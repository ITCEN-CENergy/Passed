package com.cenergy.passed_backend.domain.roadmap.skillgap;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.domain.roadmap.skillgap.merge.CompetencyGapMergeService;
import com.cenergy.passed_backend.domain.roadmap.skillgap.merge.CompetencyPriorityPolicy;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.CompetencyGapMergeInput;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.roadmap.skillgap.validation.SkillGapResponseValidator;
import com.cenergy.passed_backend.domain.skillgap.application.MockSkillGapService;
import com.cenergy.passed_backend.domain.skillgap.application.SkillGapService;
import com.cenergy.passed_backend.domain.skillgap.dto.SkillGapResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetencyGapMergeServiceTest {
    private final CompetencyGapMergeService service =
            new CompetencyGapMergeService(new CompetencyPriorityPolicy());

    @Test
    void mergesSameCompetencyAndKeepsDifferentCompetenciesSeparate() {
        List<MergedCompetencyGap> result = service.merge(List.of(
                input(102L, 1002L, gap(1L, "Docker", 2, 3, RequirementType.PREFERRED),
                        gap(2L, "AWS", 0, 2, RequirementType.RELATED)),
                input(101L, 1001L, gap(1L, "Docker", 1, 4, RequirementType.REQUIRED))));

        assertThat(result).hasSize(2);
        MergedCompetencyGap docker = byId(result, 1L);
        assertThat(docker.currentLevel()).isEqualTo(2);
        assertThat(docker.targetLevel()).isEqualTo(4);
        assertThat(docker.gapLevel()).isEqualTo(2);
        assertThat(docker.frequency()).isEqualTo(2);
        assertThat(docker.requirementType()).isEqualTo(RequirementType.REQUIRED);
        assertThat(docker.sources()).extracting("jobPostingId").containsExactly(101L, 102L);
    }

    @Test
    void rejectsInconsistentNameOrCategory() {
        assertInvalid(() -> service.merge(List.of(
                input(101L, 1L, gap(1L, "Docker", 1, 3, RequirementType.REQUIRED)),
                input(102L, 2L, gap(1L, "docker", 1, 3, RequirementType.REQUIRED)))));

        assertInvalid(() -> service.merge(List.of(
                input(101L, 1L, gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                        1, 3, RequirementType.REQUIRED)),
                input(102L, 2L, gap(1L, "Docker", CompetencyCategory.EXPERIENCE,
                        1, 3, RequirementType.REQUIRED)))));
    }

    @Test
    void removesIdenticalDuplicateButRejectsConflictingDuplicate() {
        CompetencyGapMergeInput duplicate = input(101L, 1001L,
                gap(1L, "Docker", 1, 3, RequirementType.REQUIRED));

        MergedCompetencyGap merged = service.merge(List.of(duplicate, duplicate)).getFirst();
        assertThat(merged.frequency()).isOne();
        assertThat(merged.sources()).hasSize(1);

        assertInvalid(() -> service.merge(List.of(duplicate,
                input(101L, 1002L, gap(1L, "Docker", 1, 3, RequirementType.REQUIRED)))));
    }

    @Test
    void recalculatesSourceAndMergedGapFromLevels() {
        ValidatedCompetencyGap first = new ValidatedCompetencyGap(1L, "Docker",
                CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 4, 4, 99, null);
        ValidatedCompetencyGap second = new ValidatedCompetencyGap(1L, "Docker",
                CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 1, 5, 0, null);

        MergedCompetencyGap result = service.merge(List.of(
                input(101L, 1L, first), input(102L, 2L, second))).getFirst();

        assertThat(result.currentLevel()).isEqualTo(4);
        assertThat(result.targetLevel()).isEqualTo(5);
        assertThat(result.gapLevel()).isOne();
        assertThat(result.sources()).extracting("gapLevel").containsExactly(0, 4);
    }

    @Test
    void preferredRequirementTakesPrecedenceOverRelated() {
        MergedCompetencyGap result = service.merge(List.of(
                input(101L, null, gap(1L, "Docker", 1, 3, RequirementType.RELATED)),
                input(102L, null, gap(1L, "Docker", 1, 3, RequirementType.PREFERRED))))
                .getFirst();

        assertThat(result.requirementType()).isEqualTo(RequirementType.PREFERRED);
        assertThat(result.priorityScore()).isEqualTo(222);
    }

    @Test
    void filtersNoLearningTargetsAndReturnsEmptyImmutableList() {
        List<MergedCompetencyGap> result = service.merge(List.of(
                input(101L, null, gap(1L, "Docker", 3, 3, RequirementType.REQUIRED)),
                input(102L, null, gap(2L, "AWS", 4, 2, RequirementType.PREFERRED))));

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.merge(List.of())).isEmpty();
    }

    @Test
    void calculatesScoreSortsAndAssignsPriorityDeterministically() {
        CompetencyGapMergeInput id10 = input(103L, null,
                gap(10L, "Ten", 1, 3, RequirementType.PREFERRED));
        CompetencyGapMergeInput id2 = input(102L, null,
                gap(2L, "Two", 1, 3, RequirementType.PREFERRED));
        CompetencyGapMergeInput required = input(101L, null,
                gap(30L, "Required", 2, 3, RequirementType.REQUIRED));

        List<MergedCompetencyGap> first = service.merge(List.of(id10, required, id2));
        List<MergedCompetencyGap> second = service.merge(List.of(id2, id10, required));

        assertThat(first).isEqualTo(second);
        assertThat(first).extracting("standardCompetencyId").containsExactly(30L, 2L, 10L);
        assertThat(first).extracting("priority").containsExactly(1, 2, 3);
        assertThat(first).extracting("priorityScore").containsExactly(311, 221, 221);
        assertThat(first).extracting("roadmapSkillKey")
                .containsExactly("competency-30", "competency-2", "competency-10");
    }

    @Test
    void usesTargetLevelAsSecondSortKey() {
        List<MergedCompetencyGap> result = service.merge(List.of(
                input(101L, null, gap(1L, "Low target", 0, 2, RequirementType.PREFERRED)),
                input(102L, null, gap(2L, "High target", 2, 4, RequirementType.PREFERRED))));

        assertThat(result).extracting("standardCompetencyId").containsExactly(2L, 1L);
    }

    @Test
    void preservesSourcesInDeterministicOrderAndAsImmutableList() {
        CompetencyGapMergeInput later = input(200L, 20L,
                gap(1L, "Docker", 1, 4, RequirementType.PREFERRED));
        CompetencyGapMergeInput earlier = input(100L, null,
                gap(1L, "Docker", 2, 4, RequirementType.PREFERRED));

        MergedCompetencyGap result = service.merge(List.of(later, earlier)).getFirst();

        assertThat(result.sources()).extracting("jobPostingId").containsExactly(100L, 200L);
        assertThat(result.sources().getFirst().reportId()).isNull();
        assertThat(result.sources().get(1).currentEvidence()).isEqualTo("evidence-1");
        assertThatThrownBy(() -> result.sources().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidContainerInputsWithDomainException() {
        assertInvalid(() -> service.merge(null));
        assertInvalid(() -> service.merge(Arrays.asList((CompetencyGapMergeInput) null)));
        assertInvalid(() -> service.merge(List.of(new CompetencyGapMergeInput(0L, null, List.of()))));
        assertInvalid(() -> service.merge(List.of(new CompetencyGapMergeInput(1L, null, null))));
        assertInvalid(() -> service.merge(List.of(new CompetencyGapMergeInput(
                1L, null, Arrays.asList((ValidatedCompetencyGap) null)))));
    }

    @Test
    void mergeInputDefensivelyCopiesGapList() {
        List<ValidatedCompetencyGap> mutable = new ArrayList<>();
        mutable.add(gap(1L, "Docker", 1, 3, RequirementType.REQUIRED));
        CompetencyGapMergeInput input = new CompetencyGapMergeInput(101L, null, mutable);
        mutable.clear();

        assertThat(input.competencyGaps()).hasSize(1);
        assertThatThrownBy(() -> input.competencyGaps().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesExternalServiceResponsesBeforeMergingInRoadmapBoundary() {
        SkillGapService externalService = new MockSkillGapService();
        SkillGapResponse firstResponse = externalService.getCompetencyGaps(101L, 10L);
        SkillGapResponse secondResponse = externalService.getCompetencyGaps(102L, 10L);
        SkillGapResponseValidator validator = new SkillGapResponseValidator();
        ValidatedSkillGapResult first = validator.validate(10L, 101L, firstResponse);
        ValidatedSkillGapResult second = validator.validate(10L, 102L, secondResponse);

        List<MergedCompetencyGap> result = service.merge(List.of(
                new CompetencyGapMergeInput(first.jobPostingId(), 1001L, first.competencyGaps()),
                new CompetencyGapMergeInput(second.jobPostingId(), 1002L, second.competencyGaps())));

        MergedCompetencyGap docker = byId(result, 1L);
        assertThat(docker.frequency()).isEqualTo(2);
        assertThat(docker.sources()).extracting("jobPostingId").containsExactly(101L, 102L);
    }

    private MergedCompetencyGap byId(List<MergedCompetencyGap> result, long id) {
        return result.stream().filter(item -> item.standardCompetencyId() == id).findFirst().orElseThrow();
    }

    private CompetencyGapMergeInput input(long jobPostingId, Long reportId, ValidatedCompetencyGap... gaps) {
        return new CompetencyGapMergeInput(jobPostingId, reportId, List.of(gaps));
    }

    private ValidatedCompetencyGap gap(long id, String name, int current, int target,
                                       RequirementType requirementType) {
        return gap(id, name, CompetencyCategory.TECHNICAL_SKILL, current, target, requirementType);
    }

    private ValidatedCompetencyGap gap(long id, String name, CompetencyCategory category,
                                       int current, int target, RequirementType requirementType) {
        return new ValidatedCompetencyGap(id, name, category, requirementType, current, target,
                Math.max(target - current, 0), "evidence-" + id);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SkillGapException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_GAP_INVALID_RESPONSE);
    }
}

package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.application.MockSkillGapService;
import com.cenergy.passed_backend.domain.skillgap.application.SkillGapService;
import com.cenergy.passed_backend.domain.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.skillgap.validation.SkillGapResponseValidator;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockSkillGapServiceTest {
    private final SkillGapService service =
            new MockSkillGapService(new SkillGapResponseValidator());

    @Test
    void printMockSkillGapResult() {
        ValidatedSkillGapResult result =
                service.getCompetencyGaps(101L, 10L);

        System.out.println("userId = " + result.userId());
        System.out.println("jobPostingId = " + result.jobPostingId());

        result.competencyGaps().forEach(gap -> {
            System.out.println("--------------------");
            System.out.println("역량 ID = " + gap.standardCompetencyId());
            System.out.println("역량명 = " + gap.standardCompetencyName());
            System.out.println("카테고리 = " + gap.category());
            System.out.println("현재 수준 = " + gap.currentLevel());
            System.out.println("목표 수준 = " + gap.targetLevel());
            System.out.println("Gap = " + gap.gapLevel());
        });
    }

    @Test
    void returnsDeterministicResultForSameInput() {
        ValidatedSkillGapResult first = service.getCompetencyGaps(101L, 10L);
        ValidatedSkillGapResult second = service.getCompetencyGaps(101L, 10L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void returnsDifferentResultsByJobPosting() {
        assertThat(service.getCompetencyGaps(101L, 10L).competencyGaps())
                .isNotEqualTo(service.getCompetencyGaps(103L, 10L).competencyGaps());
    }

    @Test
    void includesRepeatedCompetenciesAcrossDifferentPostings() {
        assertThat(service.getCompetencyGaps(101L, 10L).competencyGaps())
                .extracting("standardCompetencyName").contains("Docker");
        assertThat(service.getCompetencyGaps(102L, 10L).competencyGaps())
                .extracting("standardCompetencyName").contains("Docker");
        assertThat(service.getCompetencyGaps(103L, 10L).competencyGaps())
                .extracting("standardCompetencyName").contains("AWS");
    }

    @Test
    void returnsEmptyGapsForConfiguredPosting() {
        assertThat(service.getCompetencyGaps(104L, 10L).competencyGaps()).isEmpty();
    }

    @Test
    void rejectsUnknownPostingExplicitly() {
        assertThatThrownBy(() -> service.getCompetencyGaps(999L, 10L))
                .isInstanceOf(SkillGapException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_GAP_NOT_FOUND);
    }
}

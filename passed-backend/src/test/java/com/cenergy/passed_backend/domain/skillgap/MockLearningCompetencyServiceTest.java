package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.application.MockLearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.application.LearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockLearningCompetencyServiceTest {
    private final LearningCompetencyService service = new MockLearningCompetencyService();

    @Test
    void printMockSkillGapResult() {
        LearningCompetencyResponse result =
                service.getLearningCompetencies(101L, 10L);

        System.out.println("userId = " + result.userId());
        System.out.println("jobPostingId = " + result.jobPostingId());

        result.competencies().forEach(gap -> {
            System.out.println("--------------------");
            System.out.println("역량 ID = " + gap.standardCompetencyId());
            System.out.println("역량명 = " + gap.standardCompetencyName());
            System.out.println("카테고리 = " + gap.category());
            System.out.println("현재 수준 = " + gap.currentLevel());
            System.out.println("목표 수준 = " + gap.targetLevel());
            System.out.println("학습 수준 = " + gap.currentLevel() + " -> " + gap.targetLevel());
        });
    }

    @Test
    void returnsDeterministicResultForSameInput() {
        LearningCompetencyResponse first = service.getLearningCompetencies(101L, 10L);
        LearningCompetencyResponse second = service.getLearningCompetencies(101L, 10L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void returnsDifferentResultsByJobPosting() {
        assertThat(service.getLearningCompetencies(101L, 10L).competencies())
                .isNotEqualTo(service.getLearningCompetencies(103L, 10L).competencies());
    }

    @Test
    void includesRepeatedCompetenciesAcrossDifferentPostings() {
        assertThat(service.getLearningCompetencies(101L, 10L).competencies())
                .extracting("standardCompetencyName").contains("Docker");
        assertThat(service.getLearningCompetencies(102L, 10L).competencies())
                .extracting("standardCompetencyName").contains("Docker");
        assertThat(service.getLearningCompetencies(103L, 10L).competencies())
                .extracting("standardCompetencyName").contains("AWS");
    }

    @Test
    void returnsEmptyCompetenciesForConfiguredPosting() {
        assertThat(service.getLearningCompetencies(104L, 10L).competencies()).isEmpty();
    }

    @Test
    void providesVariedLearningCompetencies() {
        assertThat(service.getLearningCompetencies(105L, 10L).competencies())
                .extracting("standardCompetencyName")
                .containsExactly("Docker", "AWS", "Java");
        assertThat(service.getLearningCompetencies(106L, 10L).competencies())
                .anySatisfy(competency -> assertThat(competency.currentLevel())
                        .isEqualTo(competency.targetLevel()));
        assertThat(service.getLearningCompetencies(107L, 10L).competencies())
                .anySatisfy(gap -> assertThat(gap.category().name()).isEqualTo("CERTIFICATION"));
        assertThat(service.getLearningCompetencies(108L, 10L).competencies())
                .anySatisfy(gap -> assertThat(gap.category().name()).isEqualTo("EXPERIENCE"));
    }

    @Test
    void rejectsUnknownPostingExplicitly() {
        assertThatThrownBy(() -> service.getLearningCompetencies(999L, 10L))
                .isInstanceOf(SkillGapException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_GAP_NOT_FOUND);
    }
}

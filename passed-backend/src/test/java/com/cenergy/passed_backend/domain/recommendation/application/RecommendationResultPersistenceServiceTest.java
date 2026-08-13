package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.EvaluatedSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationScoreResult;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationResultPersistenceServiceTest {
    private RecommendationRunRepository runRepository;
    private JobPostingRepository jobPostingRepository;
    private SkillRepository skillRepository;
    private JobRecommendationRepository recommendationRepository;
    private JobRecommendationSkillDetailRepository skillDetailRepository;
    private RecommendationResultPersistenceService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(RecommendationRunRepository.class);
        jobPostingRepository = mock(JobPostingRepository.class);
        skillRepository = mock(SkillRepository.class);
        recommendationRepository = mock(JobRecommendationRepository.class);
        skillDetailRepository = mock(JobRecommendationSkillDetailRepository.class);
        service = new RecommendationResultPersistenceService(
                runRepository,
                jobPostingRepository,
                skillRepository,
                recommendationRepository,
                skillDetailRepository
        );
    }

    @Test
    void persistsSingleRecommendationAndCompletesRun() {
        RecommendationRun run = mock(RecommendationRun.class);
        JobPosting posting = mock(JobPosting.class);
        Skill skill = mock(Skill.class);
        when(runRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(run));
        when(posting.getId()).thenReturn(100L);
        when(jobPostingRepository.findAllById(List.of(100L))).thenReturn(List.of(posting));
        when(skill.getId()).thenReturn(12L);
        when(skillRepository.findAllByIdIn(List.of(12L))).thenReturn(List.of(skill));

        service.complete(
                10L,
                gradedRecommendation(),
                new RecommendationExplanation(100L, "단일 공고 추천 이유")
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<JobRecommendation>> recommendationCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(recommendationRepository).saveAll(recommendationCaptor.capture());
        JobRecommendation saved = recommendationCaptor.getValue().iterator().next();
        assertEquals(posting, saved.getJobPosting());
        assertEquals(RecommendationGrade.RECOMMENDED, saved.getRecommendationGrade());
        assertEquals(1, saved.getRankOrder());
        assertEquals("단일 공고 추천 이유", saved.getReason());
        assertEquals(new BigDecimal("80.0000"), saved.getTotalScore());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<JobRecommendationSkillDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(skillDetailRepository).saveAll(detailCaptor.capture());
        JobRecommendationSkillDetail detail = detailCaptor.getValue().iterator().next();
        assertEquals(saved, detail.getJobRecommendation());
        assertEquals(skill, detail.getSkill());
        assertEquals(JobPostingSkillType.REQUIRED, detail.getSkillType());
        verify(recommendationRepository).flush();
        verify(run).complete(1, 1);
    }

    @Test
    void rejectsExplanationForDifferentPostingBeforePersistence() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.complete(
                        10L,
                        gradedRecommendation(),
                        new RecommendationExplanation(999L, "잘못된 공고 설명")
                )
        );

        assertEquals(
                "Recommendation and explanation must reference the same job posting",
                exception.getMessage()
        );
        verify(runRepository, never()).findByIdForUpdate(any());
    }

    private GradedRecommendation gradedRecommendation() {
        EvaluatedSkillDetail detail = new EvaluatedSkillDetail(
                12L,
                "Java",
                JobPostingSkillType.REQUIRED,
                (short) 3,
                (short) 3,
                SkillEvaluationType.LEVEL,
                true,
                true,
                true,
                BigDecimal.ONE.setScale(4),
                new BigDecimal("60.0000"),
                new BigDecimal("60.0000"),
                new BigDecimal("10.0000")
        );
        RecommendationScoreResult score = new RecommendationScoreResult(
                100L,
                new BigDecimal("80.0000"),
                new BigDecimal("60.0000"),
                new BigDecimal("10.0000"),
                BigDecimal.ZERO.setScale(4),
                new BigDecimal("10.0000"),
                1,
                1,
                BigDecimal.ONE.setScale(4),
                BigDecimal.ONE.setScale(4),
                1,
                1,
                RecommendationCandidateTier.PRIMARY,
                List.of(detail)
        );
        return new GradedRecommendation(score, RecommendationGrade.RECOMMENDED, 30);
    }
}

package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationRunContext;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationScoreResult;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.application.model.SinglePostingRecommendationRunContext;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateOneRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationOneServiceTest {
    private CurrentUserIdProvider currentUserIdProvider;
    private RecommendationRunStartService runStartService;
    private RecommendationCandidateLoader candidateLoader;
    private RequiredSkillEvaluator requiredSkillEvaluator;
    private RecommendationDetailedEvaluationService detailedEvaluationService;
    private RecommendationGradeResolver gradeResolver;
    private RecommendationExplanationService explanationService;
    private RecommendationResultPersistenceService persistenceService;
    private RecommendationRunFailureService failureService;
    private RecommendationOneService service;

    @BeforeEach
    void setUp() {
        currentUserIdProvider = mock(CurrentUserIdProvider.class);
        runStartService = mock(RecommendationRunStartService.class);
        candidateLoader = mock(RecommendationCandidateLoader.class);
        requiredSkillEvaluator = mock(RequiredSkillEvaluator.class);
        detailedEvaluationService = mock(RecommendationDetailedEvaluationService.class);
        gradeResolver = mock(RecommendationGradeResolver.class);
        explanationService = mock(RecommendationExplanationService.class);
        persistenceService = mock(RecommendationResultPersistenceService.class);
        failureService = mock(RecommendationRunFailureService.class);
        service = new RecommendationOneService(
                currentUserIdProvider,
                runStartService,
                candidateLoader,
                requiredSkillEvaluator,
                detailedEvaluationService,
                gradeResolver,
                explanationService,
                persistenceService,
                failureService
        );
    }

    @Test
    void evaluatesExplainsPersistsAndReturnsSingleRecommendation() {
        RecommendationCreateOneRequest request = new RecommendationCreateOneRequest(100L);
        SinglePostingRecommendationRunContext context = context();
        PostingSkillBundle bundle = PostingSkillBundle.empty();
        RequiredSkillEvaluation evaluation = mock(RequiredSkillEvaluation.class);
        RecommendationScoreResult score = mock(RecommendationScoreResult.class);
        GradedRecommendation graded = mock(GradedRecommendation.class);
        RecommendationExplanation explanation = new RecommendationExplanation(100L, "추천 이유");
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        when(runStartService.startForSinglePosting(2L, 100L)).thenReturn(context);
        when(candidateLoader.loadByJobPostingId(100L)).thenReturn(bundle);
        when(requiredSkillEvaluator.evaluate(bundle, context.run().userSkills()))
                .thenReturn(evaluation);
        when(detailedEvaluationService.evaluate(
                100L,
                bundle,
                evaluation,
                context.run().userSkills(),
                context.run().policy()
        )).thenReturn(score);
        when(gradeResolver.resolve(score, context.run().gradeRules())).thenReturn(graded);
        when(explanationService.generate(graded)).thenReturn(explanation);

        var response = service.start(request);

        assertEquals(10L, response.runId());
        assertEquals(RecommendationRunStatus.COMPLETED, response.status());
        assertEquals(context.run().startedAt(), response.startedAt());
        InOrder order = inOrder(
                runStartService,
                candidateLoader,
                requiredSkillEvaluator,
                detailedEvaluationService,
                gradeResolver,
                explanationService,
                persistenceService
        );
        order.verify(runStartService).startForSinglePosting(2L, 100L);
        order.verify(candidateLoader).loadByJobPostingId(100L);
        order.verify(requiredSkillEvaluator).evaluate(bundle, context.run().userSkills());
        order.verify(detailedEvaluationService).evaluate(
                100L,
                bundle,
                evaluation,
                context.run().userSkills(),
                context.run().policy()
        );
        order.verify(gradeResolver).resolve(score, context.run().gradeRules());
        order.verify(explanationService).generate(graded);
        order.verify(persistenceService).complete(10L, graded, explanation);
    }

    @Test
    void marksStartedRunFailedAndPreservesFailureUpdateException() {
        SinglePostingRecommendationRunContext context = context();
        RuntimeException calculationFailure = new RuntimeException("calculation failed");
        RuntimeException failureUpdateFailure = new RuntimeException("failure update failed");
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        when(runStartService.startForSinglePosting(2L, 100L)).thenReturn(context);
        when(candidateLoader.loadByJobPostingId(100L)).thenThrow(calculationFailure);
        org.mockito.Mockito.doThrow(failureUpdateFailure)
                .when(failureService).fail(10L, calculationFailure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.start(new RecommendationCreateOneRequest(100L))
        );

        assertSame(calculationFailure, actual);
        assertEquals(List.of(failureUpdateFailure), List.of(actual.getSuppressed()));
        verify(failureService).fail(10L, calculationFailure);
    }

    private SinglePostingRecommendationRunContext context() {
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        List<RecommendationGradeRule> gradeRules = List.of(mock(RecommendationGradeRule.class));
        RecommendationRunContext run = new RecommendationRunContext(
                10L,
                policy,
                gradeRules,
                List.of(new UserSkillData(12L, (short) 3, true)),
                1,
                "a".repeat(64),
                OffsetDateTime.parse("2026-08-12T12:00:00+09:00")
        );
        return new SinglePostingRecommendationRunContext(run, 100L);
    }
}

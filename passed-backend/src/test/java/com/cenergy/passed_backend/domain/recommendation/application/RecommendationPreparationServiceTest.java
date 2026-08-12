package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationPreparationServiceTest {
    private RecommendationRunStartService runStartService;
    private CurrentUserIdProvider currentUserIdProvider;
    private RecommendationCandidateSelectionService candidateSelectionService;
    private RecommendationDetailedEvaluationService detailedEvaluationService;
    private RecommendationGradeResolver gradeResolver;
    private RecommendationTopKSelector topKSelector;
    private RecommendationExplanationService explanationService;
    private RecommendationResultPersistenceService persistenceService;
    private RecommendationRunFailureService failureService;
    private RecommendationPreparationService service;

    @BeforeEach
    void setUp() {
        currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        runStartService = mock(RecommendationRunStartService.class);
        candidateSelectionService = mock(RecommendationCandidateSelectionService.class);
        detailedEvaluationService = mock(RecommendationDetailedEvaluationService.class);
        gradeResolver = mock(RecommendationGradeResolver.class);
        topKSelector = mock(RecommendationTopKSelector.class);
        explanationService = mock(RecommendationExplanationService.class);
        persistenceService = mock(RecommendationResultPersistenceService.class);
        failureService = mock(RecommendationRunFailureService.class);
        service = new RecommendationPreparationService(
                currentUserIdProvider,
                runStartService,
                candidateSelectionService,
                detailedEvaluationService,
                gradeResolver,
                topKSelector,
                explanationService,
                persistenceService,
                failureService
        );
    }

    @Test
    void calculatesExplainsAndPersistsOnlyRankedResultsInOrder() {
        RecommendationCreateRequest request = new RecommendationCreateRequest(
                8L,
                List.of(239L)
        );
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        when(policy.getPolicyCode()).thenReturn("SKILL_MATCH");
        when(policy.getVersion()).thenReturn("v1");
        PreferenceRecommendationRunContext context = new PreferenceRecommendationRunContext(
                new RecommendationRunContext(
                        10L,
                        policy,
                        List.of(
                                mock(RecommendationGradeRule.class), mock(RecommendationGradeRule.class),
                                mock(RecommendationGradeRule.class), mock(RecommendationGradeRule.class)
                        ),
                        List.of(new UserSkillData(12L, (short) 3, true)),
                        1,
                        "a".repeat(64),
                        OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
                ),
                8L,
                List.of(239L)
        );
        RecommendationCandidateSelectionResult selection = new RecommendationCandidateSelectionResult(
                Map.of(100L, PostingSkillBundle.empty()),
                Map.of()
        );
        List<RecommendationScoreResult> scores = List.of();
        List<GradedRecommendation> graded = List.of();
        List<RankedRecommendation> ranked = List.of();
        Map<Long, RecommendationExplanation> explanations = Map.of();
        when(runStartService.startForPreference(2L, request)).thenReturn(context);
        when(candidateSelectionService.select(context.jobRoleIds(), context.run().userSkills(), policy))
                .thenReturn(selection);
        when(detailedEvaluationService.evaluateAll(selection, context.run().userSkills(), policy))
                .thenReturn(scores);
        when(gradeResolver.resolveAll(scores, context.run().gradeRules())).thenReturn(graded);
        when(topKSelector.select(graded)).thenReturn(ranked);
        when(explanationService.generateAll(ranked)).thenReturn(explanations);

        var response = service.prepare(request);

        assertEquals(RecommendationRunStatus.COMPLETED, response.status());
        assertEquals(1, response.candidatePostingCount());
        assertEquals(0, response.requiredQualifiedPostingCount());
        InOrder order = inOrder(
                runStartService,
                candidateSelectionService,
                detailedEvaluationService,
                gradeResolver,
                topKSelector,
                explanationService,
                persistenceService
        );
        order.verify(runStartService).startForPreference(2L, request);
        order.verify(candidateSelectionService).select(context.jobRoleIds(), context.run().userSkills(), policy);
        order.verify(detailedEvaluationService).evaluateAll(selection, context.run().userSkills(), policy);
        order.verify(gradeResolver).resolveAll(scores, context.run().gradeRules());
        order.verify(topKSelector).select(graded);
        order.verify(explanationService).generateAll(ranked);
        order.verify(persistenceService).complete(10L, ranked, explanations, 1, 0);
    }

    @Test
    void marksStartedRunFailedWhenCalculationThrows() {
        RecommendationCreateRequest request = new RecommendationCreateRequest(8L, List.of());
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        PreferenceRecommendationRunContext context = new PreferenceRecommendationRunContext(
                new RecommendationRunContext(
                        10L,
                        policy,
                        List.of(),
                        List.of(new UserSkillData(12L, (short) 3, false)),
                        0,
                        "a".repeat(64),
                        OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
                ),
                8L,
                List.of()
        );
        RuntimeException failure = new RuntimeException("calculation failed");
        when(runStartService.startForPreference(2L, request)).thenReturn(context);
        when(candidateSelectionService.select(any(), any(), same(policy))).thenThrow(failure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.prepare(request)
        );

        assertEquals(failure, actual);
        verify(failureService).fail(10L, failure);
    }
}

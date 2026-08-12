package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateResponse;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecommendationPreparationService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RecommendationRunStartService runStartService;
    private final RecommendationCandidateSelectionService candidateSelectionService;
    private final RecommendationDetailedEvaluationService detailedEvaluationService;
    private final RecommendationGradeResolver gradeResolver;
    private final RecommendationTopKSelector topKSelector;
    private final RecommendationExplanationService explanationService;
    private final RecommendationResultPersistenceService persistenceService;
    private final RecommendationRunFailureService failureService;

    public RecommendationPreparationService(
            CurrentUserIdProvider currentUserIdProvider,
            RecommendationRunStartService runStartService,
            RecommendationCandidateSelectionService candidateSelectionService,
            RecommendationDetailedEvaluationService detailedEvaluationService,
            RecommendationGradeResolver gradeResolver,
            RecommendationTopKSelector topKSelector,
            RecommendationExplanationService explanationService,
            RecommendationResultPersistenceService persistenceService,
            RecommendationRunFailureService failureService
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.runStartService = runStartService;
        this.candidateSelectionService = candidateSelectionService;
        this.detailedEvaluationService = detailedEvaluationService;
        this.gradeResolver = gradeResolver;
        this.topKSelector = topKSelector;
        this.explanationService = explanationService;
        this.persistenceService = persistenceService;
        this.failureService = failureService;
    }

    public RecommendationCreateResponse prepare(RecommendationCreateRequest request) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        PreferenceRecommendationRunContext context = runStartService.startForPreference(userId, request);
        try {
            RecommendationCandidateSelectionResult selection = candidateSelectionService.select(
                    context.jobRoleIds(),
                    context.run().userSkills(),
                    context.run().policy()
            );
            List<RecommendationScoreResult> scores = detailedEvaluationService.evaluateAll(
                    selection,
                    context.run().userSkills(),
                    context.run().policy()
            );
            List<GradedRecommendation> graded = gradeResolver.resolveAll(
                    scores,
                    context.run().gradeRules()
            );
            List<RankedRecommendation> ranked = topKSelector.select(graded);
            Map<Long, RecommendationExplanation> explanations = explanationService.generateAll(ranked);
            persistenceService.complete(
                    context.run().recommendationRunId(),
                    ranked,
                    explanations,
                    selection.candidatePostingCount(),
                    selection.requiredQualifiedPostingCount()
            );
            return response(context, selection);
        } catch (RuntimeException exception) {
            try {
                failureService.fail(context.run().recommendationRunId(), exception);
            } catch (RuntimeException failureUpdateException) {
                exception.addSuppressed(failureUpdateException);
            }
            throw exception;
        }
    }

    private RecommendationCreateResponse response(
            PreferenceRecommendationRunContext context,
            RecommendationCandidateSelectionResult selection
    ) {
        return new RecommendationCreateResponse(
                context.run().recommendationRunId(),
                RecommendationRunStatus.COMPLETED,
                selection.candidatePostingCount(),
                selection.requiredQualifiedPostingCount(),
                context.industryId(),
                context.jobRoleIds(),
                context.run().startedAt()
        );
    }
}

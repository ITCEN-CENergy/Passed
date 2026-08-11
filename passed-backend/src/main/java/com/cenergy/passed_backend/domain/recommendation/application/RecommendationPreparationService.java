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
        RecommendationRunContext context = runStartService.start(userId, request);
        try {
            RecommendationCandidateSelectionResult selection = candidateSelectionService.select(
                    context.jobRoleIds(),
                    context.userSkills(),
                    context.policy()
            );
            List<RecommendationScoreResult> scores = detailedEvaluationService.evaluate(
                    selection,
                    context.userSkills(),
                    context.policy()
            );
            List<GradedRecommendation> graded = gradeResolver.resolveAll(
                    scores,
                    context.gradeRules()
            );
            List<RankedRecommendation> ranked = topKSelector.select(graded);
            Map<Long, RecommendationExplanation> explanations = explanationService.generate(ranked);
            persistenceService.complete(
                    context.recommendationRunId(),
                    ranked,
                    explanations,
                    selection.candidatePostingCount(),
                    selection.requiredQualifiedPostingCount()
            );
            return response(context, selection);
        } catch (RuntimeException exception) {
            try {
                failureService.fail(context.recommendationRunId(), exception);
            } catch (RuntimeException failureUpdateException) {
                exception.addSuppressed(failureUpdateException);
            }
            throw exception;
        }
    }

    private RecommendationCreateResponse response(
            RecommendationRunContext context,
            RecommendationCandidateSelectionResult selection
    ) {
        return new RecommendationCreateResponse(
                context.recommendationRunId(),
                RecommendationRunStatus.COMPLETED,
                selection.candidatePostingCount(),
                selection.requiredQualifiedPostingCount(),
                context.industryId(),
                context.jobRoleIds(),
                context.startedAt()
        );
    }
}

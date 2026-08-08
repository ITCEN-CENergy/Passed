package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareResponse;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecommendationPreparationService {
    private final RecommendationRunStartService runStartService;
    private final RecommendationCandidateSelectionService candidateSelectionService;
    private final RecommendationDetailedEvaluationService detailedEvaluationService;
    private final RecommendationGradeResolver gradeResolver;
    private final RecommendationTopKSelector topKSelector;
    private final RecommendationExplanationService explanationService;
    private final RecommendationResultPersistenceService persistenceService;
    private final RecommendationRunFailureService failureService;

    public RecommendationPreparationService(
            RecommendationRunStartService runStartService,
            RecommendationCandidateSelectionService candidateSelectionService,
            RecommendationDetailedEvaluationService detailedEvaluationService,
            RecommendationGradeResolver gradeResolver,
            RecommendationTopKSelector topKSelector,
            RecommendationExplanationService explanationService,
            RecommendationResultPersistenceService persistenceService,
            RecommendationRunFailureService failureService
    ) {
        this.runStartService = runStartService;
        this.candidateSelectionService = candidateSelectionService;
        this.detailedEvaluationService = detailedEvaluationService;
        this.gradeResolver = gradeResolver;
        this.topKSelector = topKSelector;
        this.explanationService = explanationService;
        this.persistenceService = persistenceService;
        this.failureService = failureService;
    }

    public RecommendationPrepareResponse prepare(RecommendationPrepareRequest request) {
        RecommendationRunContext context = runStartService.start(request);
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
                    explanations
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

    private RecommendationPrepareResponse response(
            RecommendationRunContext context,
            RecommendationCandidateSelectionResult selection
    ) {
        return new RecommendationPrepareResponse(
                context.recommendationRunId(),
                RecommendationRunStatus.COMPLETED,
                context.policy().getPolicyCode(),
                context.policy().getVersion(),
                context.gradeRules().size(),
                context.userSkills().size(),
                context.importantSkillCount(),
                selection.candidatePostingCount(),
                selection.requiredQualifiedPostingCount(),
                context.userSkillSnapshotHash(),
                context.industryId(),
                context.jobRoleIds()
        );
    }
}

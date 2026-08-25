package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateOneRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateOneResponse;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;

@Service
public class RecommendationOneService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RecommendationRunStartService runStartService;
    private final RecommendationCandidateLoader candidateLoader;
    private final RequiredSkillEvaluator requiredSkillEvaluator;
    private final RecommendationSkillVerificationService skillVerificationService;
    private final RecommendationDetailedEvaluationService detailedEvaluationService;
    private final RecommendationGradeResolver gradeResolver;
    private final RecommendationExplanationService explanationService;
    private final RecommendationResultPersistenceService persistenceService;
    private final RecommendationRunFailureService failureService;

    public RecommendationOneService(
            CurrentUserIdProvider currentUserIdProvider,
            RecommendationRunStartService runStartService,
            RecommendationCandidateLoader candidateLoader,
            RequiredSkillEvaluator requiredSkillEvaluator,
            RecommendationSkillVerificationService skillVerificationService,
            RecommendationDetailedEvaluationService detailedEvaluationService,
            RecommendationGradeResolver gradeResolver,
            RecommendationExplanationService explanationService,
            RecommendationResultPersistenceService persistenceService,
            RecommendationRunFailureService failureService
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.runStartService = runStartService;
        this.candidateLoader = candidateLoader;
        this.requiredSkillEvaluator = requiredSkillEvaluator;
        this.skillVerificationService = skillVerificationService;
        this.detailedEvaluationService = detailedEvaluationService;
        this.gradeResolver = gradeResolver;
        this.explanationService = explanationService;
        this.persistenceService = persistenceService;
        this.failureService = failureService;
    }

    public RecommendationCreateOneResponse start(RecommendationCreateOneRequest request) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        SinglePostingRecommendationRunContext context = runStartService.startForSinglePosting(
                userId,
                request.jobPostingId()
        );
        try {
            // 공고의 REQUIRED·PREFERRED·RELATED 스킬과 사용자 스킬의 상세 점수 계산
            PostingSkillBundle postingSkillBundle = candidateLoader.loadByJobPostingId(request.jobPostingId());
            var effectiveUserSkills = skillVerificationService.enrich(
                    userId,
                    java.util.List.of(postingSkillBundle),
                    context.run().userSkills()
            );
            RequiredSkillEvaluation evaluation =
                    requiredSkillEvaluator.evaluate(postingSkillBundle, effectiveUserSkills);
            // 사용자 스킬별 점수 결과 계산
            RecommendationScoreResult score = detailedEvaluationService.evaluate(
                    context.jobPostingId(),
                    postingSkillBundle,
                    evaluation,
                    effectiveUserSkills,
                    context.run().policy()
            );
            // 정책 등급 규칙을 적용하여 공고별 추천 등급 결정
            GradedRecommendation grade = gradeResolver.resolve(
                    score,
                    context.run().gradeRules()
            );

            // 최종 추천 공고에 대한 추천 사유·강점·보완점 생성
            RecommendationExplanation explanation = explanationService.generate(grade);
            // 공고의 스킬별 평가 상세, 추천 설명을 DB에 저장
            // 정상 처리 완료 시 추천 실행 상태를 COMPLETED로 변경
            persistenceService.complete(
                    context.run().recommendationRunId(),
                    grade,
                    explanation
            );
            // 실행 결과 정보를 RecommendationCreateOneResponse로 반환
            return new RecommendationCreateOneResponse(
                    context.run().recommendationRunId(),
                    RecommendationRunStatus.COMPLETED,
                    context.run().startedAt()
            );

        } catch (RuntimeException exception) {
            try {
                // 처리 도중 예외 발생 시 별도 트랜잭션을 통해 추천 실행 상태를 FAILED로 변경
                failureService.fail(context.run().recommendationRunId(), exception);
            } catch (RuntimeException failureUpdateException) {
                exception.addSuppressed(failureUpdateException);
            }
            throw exception;
        }
    }
}

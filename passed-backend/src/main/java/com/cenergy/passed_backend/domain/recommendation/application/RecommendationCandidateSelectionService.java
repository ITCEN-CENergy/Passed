package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationCandidateSelectionResult;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationCandidateSelectionService {
    private final RecommendationCandidateLoader candidateLoader;
    private final RequiredSkillEvaluator requiredSkillEvaluator;
    private final RequiredSkillFilter requiredSkillFilter;
    private final RecommendationSkillVerificationService skillVerificationService;

    public RecommendationCandidateSelectionService(
            RecommendationCandidateLoader candidateLoader,
            RequiredSkillEvaluator requiredSkillEvaluator,
            RequiredSkillFilter requiredSkillFilter,
            RecommendationSkillVerificationService skillVerificationService
    ) {
        this.candidateLoader = candidateLoader;
        this.requiredSkillEvaluator = requiredSkillEvaluator;
        this.requiredSkillFilter = requiredSkillFilter;
        this.skillVerificationService = skillVerificationService;
    }

    public RecommendationCandidateSelectionResult select(
            Long userId,
            Collection<Long> jobRoleIds,
            Collection<UserSkillData> userSkills,
            RecommendationScoringPolicy policy
    ) {
        Map<Long, PostingSkillBundle> candidates =
                candidateLoader.loadByJobRoleIds(jobRoleIds);

        List<UserSkillData> effectiveUserSkills = skillVerificationService.enrich(
                userId,
                candidates.values(),
                userSkills
        );

        Map<Long, RequiredSkillEvaluation> evaluations =
                requiredSkillEvaluator.evaluateAll(candidates, effectiveUserSkills);

        Map<Long, RequiredSkillEvaluation> qualified =
                requiredSkillFilter.filter(evaluations, policy);
        return new RecommendationCandidateSelectionResult(
                candidates,
                qualified,
                effectiveUserSkills
        );
    }
}

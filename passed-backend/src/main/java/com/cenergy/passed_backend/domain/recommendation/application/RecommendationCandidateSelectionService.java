package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationCandidateSelectionResult;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

@Service
public class RecommendationCandidateSelectionService {
    private final RecommendationCandidateLoader candidateLoader;
    private final RequiredSkillFilter requiredSkillFilter;

    public RecommendationCandidateSelectionService(
            RecommendationCandidateLoader candidateLoader,
            RequiredSkillFilter requiredSkillFilter
    ) {
        this.candidateLoader = candidateLoader;
        this.requiredSkillFilter = requiredSkillFilter;
    }

    public RecommendationCandidateSelectionResult select(
            Collection<Long> jobRoleIds,
            Collection<UserSkillData> userSkills,
            RecommendationScoringPolicy policy
    ) {
        Map<Long, PostingSkillBundle> candidates = candidateLoader.loadByJobRoleIds(jobRoleIds);
        Map<Long, RequiredSkillEvaluation> requiredQualifiedCandidates = requiredSkillFilter.filter(
                candidates,
                userSkills,
                policy
        );
        return new RecommendationCandidateSelectionResult(
                candidates,
                requiredQualifiedCandidates
        );
    }
}

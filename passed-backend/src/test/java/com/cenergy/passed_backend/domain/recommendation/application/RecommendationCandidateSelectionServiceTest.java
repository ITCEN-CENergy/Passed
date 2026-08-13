package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationCandidateSelectionResult;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationCandidateSelectionServiceTest {
    private RecommendationCandidateLoader candidateLoader;
    private RequiredSkillEvaluator requiredSkillEvaluator;
    private RequiredSkillFilter requiredSkillFilter;
    private RecommendationCandidateSelectionService service;

    @BeforeEach
    void setUp() {
        candidateLoader = mock(RecommendationCandidateLoader.class);
        requiredSkillEvaluator = mock(RequiredSkillEvaluator.class);
        requiredSkillFilter = mock(RequiredSkillFilter.class);
        service = new RecommendationCandidateSelectionService(
                candidateLoader,
                requiredSkillEvaluator,
                requiredSkillFilter
        );
    }

    @Test
    void loadsCandidatesBeforeApplyingRequiredSkillFilter() {
        List<Long> jobRoleIds = List.of(227L, 239L);
        List<UserSkillData> userSkills = List.of(new UserSkillData(12L, (short) 3, true));
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        Map<Long, PostingSkillBundle> candidates = Map.of(
                100L, PostingSkillBundle.empty(),
                200L, PostingSkillBundle.empty()
        );
        Map<Long, RequiredSkillEvaluation> qualified = Map.of(
                100L, mock(RequiredSkillEvaluation.class)
        );
        when(candidateLoader.loadByJobRoleIds(jobRoleIds)).thenReturn(candidates);
        Map<Long, RequiredSkillEvaluation> evaluations = Map.of(
                100L, mock(RequiredSkillEvaluation.class),
                200L, mock(RequiredSkillEvaluation.class)
        );
        when(requiredSkillEvaluator.evaluateAll(candidates, userSkills)).thenReturn(evaluations);
        when(requiredSkillFilter.filter(evaluations, policy)).thenReturn(qualified);

        RecommendationCandidateSelectionResult result = service.select(
                jobRoleIds,
                userSkills,
                policy
        );

        assertEquals(2, result.candidatePostingCount());
        assertEquals(1, result.requiredQualifiedPostingCount());
        InOrder executionOrder = inOrder(
                candidateLoader,
                requiredSkillEvaluator,
                requiredSkillFilter
        );
        executionOrder.verify(candidateLoader).loadByJobRoleIds(jobRoleIds);
        executionOrder.verify(requiredSkillEvaluator)
                .evaluateAll(same(candidates), same(userSkills));
        executionOrder.verify(requiredSkillFilter)
                .filter(same(evaluations), same(policy));
    }
}

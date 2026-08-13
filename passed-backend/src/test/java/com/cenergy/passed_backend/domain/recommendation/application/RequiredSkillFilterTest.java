package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiredSkillFilterTest {
    private RequiredSkillFilter filter;
    private RecommendationScoringPolicy policy;

    @BeforeEach
    void setUp() {
        filter = new RequiredSkillFilter();
        policy = mock(RecommendationScoringPolicy.class);
        when(policy.getRequiredCoverageThreshold()).thenReturn(new BigDecimal("0.5000"));
    }

    @Test
    void keepsOnlyPostingsMeetingRequiredCoverageThreshold() {
        RequiredSkillEvaluation qualifiedEvaluation = evaluation("0.5000");
        RequiredSkillEvaluation rejectedEvaluation = evaluation("0.3333");
        Map<Long, RequiredSkillEvaluation> evaluations = Map.of(
                100L, qualifiedEvaluation,
                200L, rejectedEvaluation
        );
        Map<Long, RequiredSkillEvaluation> result = filter.filter(evaluations, policy);

        assertEquals(1, result.size());
        assertTrue(result.containsKey(100L));
        assertFalse(result.containsKey(200L));
        assertEquals(qualifiedEvaluation, result.get(100L));
    }

    @Test
    void keepsEvaluationAboveRequiredCoverageThreshold() {
        RequiredSkillEvaluation evaluation = evaluation("0.7500");
        Map<Long, RequiredSkillEvaluation> evaluations = Map.of(100L, evaluation);

        Map<Long, RequiredSkillEvaluation> result = filter.filter(evaluations, policy);

        assertEquals(Map.of(100L, evaluation), result);
    }

    private RequiredSkillEvaluation evaluation(String coverageRate) {
        return new RequiredSkillEvaluation(
                1,
                0,
                new BigDecimal(coverageRate),
                BigDecimal.ZERO.setScale(4),
                List.of()
        );
    }
}

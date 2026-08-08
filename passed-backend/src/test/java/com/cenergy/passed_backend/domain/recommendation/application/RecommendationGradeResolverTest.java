package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationScoreResult;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationGradeResolverTest {
    @Test
    void selectsFirstRuleWhoseFourConditionsAllMatch() {
        RecommendationGradeRule highly = rule(
                RecommendationGrade.HIGHLY_RECOMMENDED,
                40,
                "85.00",
                "0.8000",
                "0.8000",
                1
        );
        RecommendationGradeRule recommended = rule(
                RecommendationGrade.RECOMMENDED,
                30,
                "70.00",
                "0.7000",
                "0.0000",
                0
        );
        RecommendationScoreResult score = score("90.0000", "0.7500", "0.9000", 1);

        GradedRecommendation result = new RecommendationGradeResolver()
                .resolveAll(List.of(score), List.of(recommended, highly))
                .getFirst();

        assertEquals(RecommendationGrade.RECOMMENDED, result.grade());
        assertEquals(30, result.gradePriority());
    }

    private RecommendationGradeRule rule(
            RecommendationGrade grade,
            int priority,
            String total,
            String coverage,
            String level,
            int importantCount
    ) {
        RecommendationGradeRule rule = mock(RecommendationGradeRule.class);
        when(rule.getRecommendationGrade()).thenReturn(grade);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getMinTotalScore()).thenReturn(new BigDecimal(total));
        when(rule.getMinRequiredCoverageRate()).thenReturn(new BigDecimal(coverage));
        when(rule.getMinRequiredLevelMatchRate()).thenReturn(new BigDecimal(level));
        when(rule.getMinImportantMatchCount()).thenReturn(importantCount);
        return rule;
    }

    private RecommendationScoreResult score(
            String total,
            String coverage,
            String level,
            int importantCount
    ) {
        return new RecommendationScoreResult(
                100L,
                new BigDecimal(total),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                1,
                new BigDecimal(coverage),
                new BigDecimal(level),
                1,
                importantCount,
                RecommendationCandidateTier.PRIMARY,
                List.of()
        );
    }
}

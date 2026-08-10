package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationExplanationServiceTest {
    @Test
    void retriesTwiceThenUsesCalculationBasedFallback() {
        RecommendationExplanationClient client = mock(RecommendationExplanationClient.class);
        RecommendationPostingSummaryLoader summaryLoader = mock(
                RecommendationPostingSummaryLoader.class
        );
        when(client.generate(any())).thenThrow(new RuntimeException("OpenAI unavailable"));
        when(summaryLoader.load(List.of(100L))).thenReturn(Map.of(
                100L,
                new RecommendationPostingSummary(100L, "백엔드 개발자", "테스트 회사")
        ));
        RecommendationScoreResult score = new RecommendationScoreResult(
                100L,
                new BigDecimal("70.0000"),
                new BigDecimal("40.0000"),
                new BigDecimal("20.0000"),
                new BigDecimal("10.0000"),
                BigDecimal.ZERO.setScale(4),
                2,
                1,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                0,
                0,
                RecommendationCandidateTier.FALLBACK,
                List.of()
        );
        RankedRecommendation ranked = new RankedRecommendation(
                new GradedRecommendation(score, RecommendationGrade.RECOMMENDED, 30),
                1
        );

        Map<Long, RecommendationExplanation> result = new RecommendationExplanationService(
                client,
                summaryLoader
        ).generate(List.of(ranked));

        verify(client, times(2)).generate(any());
        assertTrue(result.get(100L).reason().contains("70.0000"));
        assertFalse(result.get(100L).strengths().isBlank());
        assertFalse(result.get(100L).weaknesses().isBlank());
    }
}

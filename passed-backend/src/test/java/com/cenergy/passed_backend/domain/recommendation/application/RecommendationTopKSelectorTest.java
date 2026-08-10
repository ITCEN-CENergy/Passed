package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RankedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationScoreResult;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationTopKSelectorTest {
    @Test
    void keepsOnlyBestTwelveAndUsesPostingIdAsFinalTieBreaker() {
        List<GradedRecommendation> candidates = LongStream.rangeClosed(1, 13)
                .mapToObj(this::recommendation)
                .toList();

        List<RankedRecommendation> result = new RecommendationTopKSelector().select(candidates);

        assertEquals(12, result.size());
        assertEquals(LongStream.rangeClosed(1, 12).boxed().toList(), result.stream()
                .map(RankedRecommendation::jobPostingId)
                .toList());
        assertEquals(IntStream.rangeClosed(1, 12).boxed().toList(), result.stream()
                .map(RankedRecommendation::rankOrder)
                .toList());
    }

    private GradedRecommendation recommendation(long postingId) {
        RecommendationScoreResult score = new RecommendationScoreResult(
                postingId,
                new BigDecimal("70.0000"),
                new BigDecimal("40.0000"),
                new BigDecimal("20.0000"),
                new BigDecimal("10.0000"),
                BigDecimal.ZERO,
                2,
                1,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                1,
                0,
                RecommendationCandidateTier.FALLBACK,
                List.of()
        );
        return new GradedRecommendation(score, RecommendationGrade.RECOMMENDED, 30);
    }
}

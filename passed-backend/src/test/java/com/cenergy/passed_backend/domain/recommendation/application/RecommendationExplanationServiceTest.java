package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
                summary()
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
                summaryLoader,
                new RecommendationSkillHighlightSelector()
        ).generate(List.of(ranked));

        verify(client, times(2)).generate(any());
        assertTrue(result.get(100L).reason().contains("테스트 회사"));
        assertTrue(result.get(100L).reason().contains("백엔드 개발자"));
    }

    @Test
    void sendsPostingContextAndRankedSkillFactsToAi() {
        RecommendationExplanationClient client = mock(RecommendationExplanationClient.class);
        RecommendationPostingSummaryLoader summaryLoader = mock(
                RecommendationPostingSummaryLoader.class
        );
        when(summaryLoader.load(List.of(100L))).thenReturn(Map.of(100L, summary()));
        when(client.generate(anyList())).thenAnswer(invocation -> {
            List<RecommendationExplanationInput> inputs = invocation.getArgument(0);
            RecommendationExplanationInput input = inputs.getFirst();
            assertEquals("LLM 서비스 API 개발", input.posting().mainDuty());
            assertEquals(List.of("TypeScript"), input.matchedSkills().stream()
                    .map(RecommendationExplanationInput.SkillFact::skillName)
                    .toList());
            assertEquals(List.of("Docker"), input.gapSkills().stream()
                    .map(RecommendationExplanationInput.SkillFact::skillName)
                    .toList());
            return List.of(new RecommendationExplanation(
                    100L,
                    "TypeScript 역량이 LLM 서비스 API 개발 업무와 연결됩니다. Docker를 보완하면 배포와 운영까지 역할을 확장할 수 있습니다."
            ));
        });

        RecommendationScoreResult score = new RecommendationScoreResult(
                100L,
                new BigDecimal("80.0000"),
                new BigDecimal("50.0000"),
                new BigDecimal("20.0000"),
                new BigDecimal("10.0000"),
                BigDecimal.ZERO.setScale(4),
                2,
                1,
                new BigDecimal("0.5000"),
                new BigDecimal("0.7500"),
                0,
                0,
                RecommendationCandidateTier.PRIMARY,
                List.of(
                        skill(1L, "TypeScript", true, new BigDecimal("1.0000")),
                        skill(2L, "Docker", false, BigDecimal.ZERO.setScale(4))
                )
        );
        RankedRecommendation ranked = new RankedRecommendation(
                new GradedRecommendation(score, RecommendationGrade.RECOMMENDED, 30),
                1
        );

        Map<Long, RecommendationExplanation> result = new RecommendationExplanationService(
                client,
                summaryLoader,
                new RecommendationSkillHighlightSelector()
        ).generate(List.of(ranked));

        assertTrue(result.get(100L).reason().contains("TypeScript"));
    }

    private RecommendationPostingSummary summary() {
        return new RecommendationPostingSummary(
                100L,
                "백엔드 개발자",
                "테스트 회사",
                "생성형 AI 서비스를 개발합니다.",
                "LLM 서비스 API 개발",
                "TypeScript 서비스 개발 역량",
                "Docker 기반 배포 경험",
                "주도적으로 문제를 해결하는 인재"
        );
    }

    private EvaluatedSkillDetail skill(
            Long id,
            String name,
            boolean satisfied,
            BigDecimal matchRate
    ) {
        return new EvaluatedSkillDetail(
                id,
                name,
                com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType.REQUIRED,
                (short) 2,
                satisfied ? (short) 2 : null,
                com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType.LEVEL,
                satisfied,
                satisfied,
                false,
                matchRate,
                new BigDecimal("20.0000"),
                satisfied ? new BigDecimal("20.0000") : BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4)
        );
    }
}

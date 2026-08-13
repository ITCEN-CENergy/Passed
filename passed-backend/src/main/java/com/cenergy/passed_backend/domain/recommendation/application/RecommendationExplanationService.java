package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationExplanationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationExplanationService.class);
    private static final int MAX_POSTING_SECTION_LENGTH = 4_000;
    private static final int MAX_TALENT_PROFILE_LENGTH = 2_000;
    private static final int MAX_ATTEMPTS = 2;

    private final RecommendationExplanationClient explanationClient;
    private final RecommendationPostingSummaryLoader postingSummaryLoader;
    private final RecommendationSkillHighlightSelector highlightSelector;

    public RecommendationExplanationService(
            RecommendationExplanationClient explanationClient,
            RecommendationPostingSummaryLoader postingSummaryLoader,
            RecommendationSkillHighlightSelector highlightSelector
    ) {
        this.explanationClient = explanationClient;
        this.postingSummaryLoader = postingSummaryLoader;
        this.highlightSelector = highlightSelector;
    }
    // 공고 설명과 계산된 스킬 사실을 근거로 통합 추천 이유를 생성한다.
    public Map<Long, RecommendationExplanation> generateAll(
            List<RankedRecommendation> recommendations
    ) {
        Objects.requireNonNull(recommendations, "recommendations must not be null");
        if (recommendations.isEmpty()) {
            return Map.of();
        }
        // 공고 id 리스트 저장
        List<Long> postingIds = recommendations.stream()
                .map(RankedRecommendation::jobPostingId)
                .toList();
        // 추천 공고들의 제목, 회사명, 상세 업무와 요구사항을 일괄 조회한다.
        Map<Long, RecommendationPostingSummary> summaries = postingSummaryLoader.load(postingIds);
        // 추천 점수와 공고 정보를 결합하여 AI 설명 생성용 입력 DTO 생성
        List<RecommendationExplanationInput> inputs = recommendations.stream()
                .map(value -> toInput(value, summaries.get(value.jobPostingId())))
                .toList();

        Map<Long, RecommendationExplanation> generated = generateWithRetry(inputs, postingIds);
        return generated != null ? generated : fallback(recommendations, summaries);
    }

    public RecommendationExplanation generate(GradedRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        Long jobPostingId = recommendation.score().jobPostingId();
        Map<Long, RecommendationPostingSummary> summaries = postingSummaryLoader.load(
                List.of(jobPostingId)
        );
        RecommendationPostingSummary summary = Objects.requireNonNull(
                summaries.get(jobPostingId),
                "Recommendation posting summary must exist"
        );
        Map<Long, RecommendationExplanation> generated = generateWithRetry(
                List.of(toInput(recommendation, summary)),
                List.of(jobPostingId)
        );
        return generated != null
                ? generated.get(jobPostingId)
                : fallback(recommendation, summary);
    }

    private Map<Long, RecommendationExplanation> generateWithRetry(
            List<RecommendationExplanationInput> inputs,
            List<Long> postingIds
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return validate(explanationClient.generate(inputs), postingIds);
            } catch (RuntimeException exception) {
                log.warn(
                        "Recommendation explanation generation failed on attempt {}/{}",
                        attempt,
                        MAX_ATTEMPTS,
                        exception
                );
            }
        }
        return null;
    }

    private RecommendationExplanationInput toInput(
            RankedRecommendation ranked,
            RecommendationPostingSummary summary
    ) {
        return toInput(ranked.recommendation(), summary);
    }

    private RecommendationExplanationInput toInput(
            GradedRecommendation recommendation,
            RecommendationPostingSummary summary
    ) {
        RecommendationScoreResult score = recommendation.score();
        RecommendationSkillHighlightSelector.Selection highlights =
                highlightSelector.selectEvaluated(score.skillDetails());

        return new RecommendationExplanationInput(
                score.jobPostingId(),
                summary.title(),
                summary.companyName(),
                new RecommendationExplanationInput.JobPostingContext(
                        clip(summary.positionDetail(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.mainDuty(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.qualification(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.preference(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.companyTalentProfile(), MAX_TALENT_PROFILE_LENGTH)
                ),
                highlights.strengths().stream().map(this::toFact).toList(),
                highlights.gaps().stream().map(this::toFact).toList()
        );
    }

    private RecommendationExplanationInput.SkillFact toFact(
            RecommendationSkillHighlightSelector.SkillFact detail
    ) {
        return new RecommendationExplanationInput.SkillFact(
                detail.skillName(), detail.skillType().name(), detail.userLevel(),
                detail.requiredLevel(), decimal(detail.matchRate()), detail.requirementSatisfied()
        );
    }

    private Map<Long, RecommendationExplanation> validate(
            List<RecommendationExplanation> explanations,
            List<Long> expectedIds
    ) {
        if (explanations == null) {
            throw new IllegalStateException("Explanation result must not be null");
        }
        Set<Long> expected = Set.copyOf(expectedIds);
        Map<Long, RecommendationExplanation> result = new LinkedHashMap<>();
        for (RecommendationExplanation value : explanations) {
            if (value == null || !expected.contains(value.jobPostingId())) {
                throw new IllegalStateException("Explanation contains an unexpected posting ID");
            }
            requireText(value.reason(), "reason");
            if (result.putIfAbsent(value.jobPostingId(), value) != null) {
                throw new IllegalStateException("Explanation contains a duplicated posting ID");
            }
        }
        if (!result.keySet().equals(expected)) {
            throw new IllegalStateException("Explanation is missing a selected posting ID");
        }
        return Map.copyOf(result);
    }

    private RecommendationExplanation fallback(
            GradedRecommendation recommendation,
            RecommendationPostingSummary summary
    ) {
        RecommendationScoreResult score = recommendation.score();
        return new RecommendationExplanation(
                score.jobPostingId(),
                fallbackReason(summary, highlightSelector.selectEvaluated(score.skillDetails()))
        );
    }

    private Map<Long, RecommendationExplanation> fallback(
            List<RankedRecommendation> recommendations,
            Map<Long, RecommendationPostingSummary> summaries
    ) {
        Map<Long, RecommendationExplanation> result = new LinkedHashMap<>();
        for (RankedRecommendation ranked : recommendations) {
            RecommendationScoreResult score = ranked.recommendation().score();
            RecommendationPostingSummary summary = summaries.get(ranked.jobPostingId());
            result.put(ranked.jobPostingId(), new RecommendationExplanation(
                    ranked.jobPostingId(),
                    fallbackReason(summary, highlightSelector.selectEvaluated(score.skillDetails()))
            ));
        }
        return Map.copyOf(result);
    }

    private String fallbackReason(
            RecommendationPostingSummary summary,
            RecommendationSkillHighlightSelector.Selection highlights
    ) {
        String matched = skillNames(highlights.strengths());
        String gaps = skillNames(highlights.gaps());

        String opening = "%s의 %s 공고는 포지션 상세와 주요 업무를 기준으로 사용자의 역량을 연결해 볼 수 있는 공고입니다."
                .formatted(summary.companyName(), summary.title());
        String matchSentence = matched.isBlank()
                ? "현재 계산 결과에서는 구체적으로 충족한 스킬이 확인되지 않아 자격요건을 추가로 점검하는 것이 좋습니다."
                : "%s 역량은 공고에 명시된 업무와 자격요건을 수행하는 데 활용할 수 있습니다."
                .formatted(matched);
        String growthSentence = gaps.isBlank()
                ? "현재 충족한 역량을 바탕으로 해당 직무의 핵심 업무까지 수행 범위를 넓혀갈 수 있습니다."
                : "%s 역량을 우선 보완하면 공고의 요구 범위에 더 폭넓게 대응하며 직무 역량을 확장할 수 있습니다."
                .formatted(gaps);
        return String.join(" ", opening, matchSentence, growthSentence);
    }

    private String skillNames(List<RecommendationSkillHighlightSelector.SkillFact> details) {
        return details.stream()
                .map(RecommendationSkillHighlightSelector.SkillFact::skillName)
                .distinct()
                .limit(2)
                .collect(Collectors.joining(", "));
    }

    private String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String decimal(java.math.BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
    }
}

package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationExplanationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationExplanationService.class);
    private static final int MAX_FACTS_PER_GROUP = 5;
    private static final int MAX_POSTING_SECTION_LENGTH = 4_000;
    private static final int MAX_TALENT_PROFILE_LENGTH = 2_000;
    private static final int MAX_ATTEMPTS = 2;

    private final RecommendationExplanationClient explanationClient;
    private final RecommendationPostingSummaryLoader postingSummaryLoader;

    public RecommendationExplanationService(
            RecommendationExplanationClient explanationClient,
            RecommendationPostingSummaryLoader postingSummaryLoader
    ) {
        this.explanationClient = explanationClient;
        this.postingSummaryLoader = postingSummaryLoader;
    }
    // 공고 설명과 계산된 스킬 사실을 근거로 통합 추천 이유를 생성한다.
    public Map<Long, RecommendationExplanation> generate(
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

        // AI 기반 추천 설명 생성을 최대 2번 요청하고, 반환된 결과를 백엔드에서 검증한 뒤,
        // 계속 실패하면 fallback 설명을 생성
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
        return fallback(recommendations, summaries);
    }

    private RecommendationExplanationInput toInput(
            RankedRecommendation ranked,
            RecommendationPostingSummary summary
    ) {
        RecommendationScoreResult score = ranked.recommendation().score();
        List<EvaluatedSkillDetail> matchedSkills = score.skillDetails().stream()
                .filter(EvaluatedSkillDetail::requirementSatisfied)
                .sorted(Comparator
                        .comparingInt(this::skillTypePriority)
                        .thenComparing(
                                EvaluatedSkillDetail::baseContributionScore,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                EvaluatedSkillDetail::matchRate,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(EvaluatedSkillDetail::skillId))
                .limit(MAX_FACTS_PER_GROUP)
                .toList();
        List<EvaluatedSkillDetail> gapSkills = score.skillDetails().stream()
                .filter(value -> !value.requirementSatisfied())
                .sorted(Comparator
                        .comparingInt(this::skillTypePriority)
                        .thenComparing(this::scoreGap, Comparator.reverseOrder())
                        .thenComparing(
                                EvaluatedSkillDetail::requiredLevel,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(EvaluatedSkillDetail::skillId))
                .limit(MAX_FACTS_PER_GROUP)
                .toList();

        return new RecommendationExplanationInput(
                ranked.jobPostingId(),
                summary.title(),
                summary.companyName(),
                new RecommendationExplanationInput.JobPostingContext(
                        clip(summary.positionDetail(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.mainDuty(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.qualification(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.preference(), MAX_POSTING_SECTION_LENGTH),
                        clip(summary.companyTalentProfile(), MAX_TALENT_PROFILE_LENGTH)
                ),
                matchedSkills.stream().map(this::toFact).toList(),
                gapSkills.stream().map(this::toFact).toList()
        );
    }

    private RecommendationExplanationInput.SkillFact toFact(EvaluatedSkillDetail detail) {
        return new RecommendationExplanationInput.SkillFact(
                detail.skillName(),
                detail.skillType().name(),
                detail.userLevel(),
                detail.requiredLevel(),
                decimal(detail.matchRate()),
                detail.requirementSatisfied()
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
                    fallbackReason(summary, score.skillDetails())
            ));
        }
        return Map.copyOf(result);
    }

    private String fallbackReason(
            RecommendationPostingSummary summary,
            List<EvaluatedSkillDetail> details
    ) {
        String matched = skillNames(details.stream()
                .filter(EvaluatedSkillDetail::requirementSatisfied)
                .sorted(Comparator
                        .comparingInt(this::skillTypePriority)
                        .thenComparing(
                                EvaluatedSkillDetail::baseContributionScore,
                                Comparator.reverseOrder()
                        ))
                .toList());
        String gaps = skillNames(details.stream()
                .filter(value -> !value.requirementSatisfied())
                .sorted(Comparator
                        .comparingInt(this::skillTypePriority)
                        .thenComparing(this::scoreGap, Comparator.reverseOrder()))
                .toList());

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

    private String skillNames(List<EvaluatedSkillDetail> details) {
        return details.stream()
                .map(EvaluatedSkillDetail::skillName)
                .distinct()
                .limit(2)
                .collect(Collectors.joining(", "));
    }

    private int skillTypePriority(EvaluatedSkillDetail detail) {
        return switch (detail.skillType()) {
            case REQUIRED -> 0;
            case PREFERRED -> 1;
            case RELATED -> 2;
        };
    }

    private BigDecimal scoreGap(EvaluatedSkillDetail detail) {
        return detail.baseMaxScore().subtract(detail.baseContributionScore());
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

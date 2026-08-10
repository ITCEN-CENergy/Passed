package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private static final int MAX_FACTS_PER_GROUP = 10;
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
    // 추천 결과에 대한 추천 이유, 강점, 보완점의 설명 생성
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
        // 추천 공고들의 제목, 회사명 등 설명 생성에 필요한 요약 정보를 일괄 조회
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
        GradedRecommendation graded = ranked.recommendation();
        RecommendationScoreResult score = graded.score();
        List<EvaluatedSkillDetail> strengths = score.skillDetails().stream()
                .filter(EvaluatedSkillDetail::owned)
                .sorted(Comparator
                        .comparing(EvaluatedSkillDetail::userImportant).reversed()
                        .thenComparing(EvaluatedSkillDetail::skillType)
                        .thenComparing(EvaluatedSkillDetail::skillId))
                .limit(MAX_FACTS_PER_GROUP)
                .toList();
        List<EvaluatedSkillDetail> gaps = score.skillDetails().stream()
                .filter(value -> !value.requirementSatisfied())
                .sorted(Comparator
                        .comparing(EvaluatedSkillDetail::skillType)
                        .thenComparing(EvaluatedSkillDetail::skillId))
                .limit(MAX_FACTS_PER_GROUP)
                .toList();

        return new RecommendationExplanationInput(
                ranked.jobPostingId(),
                summary.title(),
                summary.companyName(),
                ranked.rankOrder(),
                graded.grade().name(),
                score.candidateTier().name(),
                decimal(score.totalScore()),
                decimal(score.requiredScore()),
                decimal(score.preferredScore()),
                decimal(score.relatedScore()),
                decimal(score.importantSkillBonus()),
                decimal(score.requiredCoverageRate()),
                decimal(score.requiredLevelMatchRate()),
                score.importantMatchCount(),
                strengths.stream().map(this::toFact).toList(),
                gaps.stream().map(this::toFact).toList()
        );
    }

    private RecommendationExplanationInput.SkillFact toFact(EvaluatedSkillDetail detail) {
        return new RecommendationExplanationInput.SkillFact(
                detail.skillName(),
                detail.skillType().name(),
                detail.evaluationType().name(),
                detail.userLevel(),
                detail.requiredLevel(),
                decimal(detail.matchRate()),
                detail.userImportant(),
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
            requireText(value.strengths(), "strengths");
            requireText(value.weaknesses(), "weaknesses");
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
            String strengths = skillNames(
                    score.skillDetails().stream().filter(EvaluatedSkillDetail::owned).toList(),
                    "매칭된 보유 스킬이 없어 기본 역량 중심의 검토가 필요합니다."
            );
            String weaknesses = skillNames(
                    score.skillDetails().stream()
                            .filter(value -> !value.requirementSatisfied())
                            .toList(),
                    "현재 확인된 스킬 기준으로 뚜렷한 미충족 항목이 없습니다."
            );
            result.put(ranked.jobPostingId(), new RecommendationExplanation(
                    ranked.jobPostingId(),
                    "%s의 %s 공고는 총점 %s점, 자격요건 보유율 %s를 기준으로 %s 등급으로 선정되었습니다."
                            .formatted(
                                    summary.companyName(),
                                    summary.title(),
                                    decimal(score.totalScore()),
                                    decimal(score.requiredCoverageRate()),
                                    ranked.recommendation().grade().name()
                            ),
                    strengths,
                    weaknesses
            ));
        }
        return Map.copyOf(result);
    }

    private String skillNames(List<EvaluatedSkillDetail> details, String emptyText) {
        if (details.isEmpty()) {
            return emptyText;
        }
        return details.stream()
                .map(EvaluatedSkillDetail::skillName)
                .distinct()
                .limit(5)
                .collect(Collectors.joining(", "));
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

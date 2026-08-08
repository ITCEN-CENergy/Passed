package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationScoreResult;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.domain.recommendation.exception.RecommendationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class RecommendationGradeResolver {
    public List<GradedRecommendation> resolveAll(
            Collection<RecommendationScoreResult> scores,
            Collection<RecommendationGradeRule> rules
    ) {
        Objects.requireNonNull(scores, "scores must not be null");
        List<RecommendationGradeRule> sortedRules = new ArrayList<>(
                Objects.requireNonNull(rules, "rules must not be null")
        );
        // priority가 높은 등급 규칙부터 검사하도록 내림차순 정렬
        sortedRules.sort(Comparator.comparingInt(RecommendationGradeRule::getPriority).reversed());

        return scores.stream()
                .map(score -> resolve(score, sortedRules))
                .toList();
    }

    private GradedRecommendation resolve(
            RecommendationScoreResult score,
            List<RecommendationGradeRule> rules
    ) {
        // 총점, 필수스킬 보유율, 숙련도 매칭률, 중요스킬 매칭 수가 등급 규칙을 모두 만족하는지 확인하고
        // 가장 높은 우선순위의 등급을 반환
        for (RecommendationGradeRule rule : rules) {
            if (score.totalScore().compareTo(rule.getMinTotalScore()) >= 0
                    && score.requiredCoverageRate().compareTo(
                            rule.getMinRequiredCoverageRate()
                    ) >= 0
                    && score.requiredLevelMatchRate().compareTo(
                            rule.getMinRequiredLevelMatchRate()
                    ) >= 0
                    && score.importantMatchCount() >= rule.getMinImportantMatchCount()) {
                return new GradedRecommendation(
                        score,
                        rule.getRecommendationGrade(),
                        rule.getPriority()
                );
            }
        }
        throw new RecommendationException(
                ErrorCode.RECOMMENDATION_POLICY_CONFIGURATION_INVALID,
                "No recommendation grade rule matches a qualified posting"
        );
    }
}

package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class RequiredSkillFilter {

    public Map<Long, RequiredSkillEvaluation> filter(
            Map<Long, RequiredSkillEvaluation> evaluations,
            RecommendationScoringPolicy policy
    ) {
        Objects.requireNonNull(evaluations, "evaluations must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        BigDecimal coverageThreshold = requireRate(
                policy.getRequiredCoverageThreshold(),
                "requiredCoverageThreshold"
        );

        Map<Long, RequiredSkillEvaluation> qualified = new LinkedHashMap<>();
        for (Map.Entry<Long, RequiredSkillEvaluation> entry : evaluations.entrySet()) {
            Long jobPostingId = requireJobPostingId(entry.getKey());
            RequiredSkillEvaluation evaluation = Objects.requireNonNull(
                    entry.getValue(),
                    "required skill evaluation must not be null"
            );

            if (evaluation.requiredCoverageRate().compareTo(coverageThreshold) >= 0) {
                qualified.put(jobPostingId, evaluation);
            }
        }

        return Collections.unmodifiableMap(qualified);
    }

    private Long requireJobPostingId(Long jobPostingId) {
        if (jobPostingId == null || jobPostingId <= 0) {
            throw new IllegalArgumentException("jobPostingId must be positive");
        }
        return jobPostingId;
    }

    private BigDecimal requireRate(
            BigDecimal value,
            String fieldName
    ) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
        return value;
    }
}

package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.recommendation.entity.RecommendationPolicyStatus;
import com.cenergy.passed_backend.recommendation.entity.RecommendationScoringPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationScoringPolicyRepository
        extends JpaRepository<RecommendationScoringPolicy, Long> {
    Optional<RecommendationScoringPolicy> findByPolicyCodeAndVersionAndStatus(
            String policyCode,
            String version,
            RecommendationPolicyStatus status
    );
}

package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.recommendation.entity.RecommendationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, Long> {
    boolean existsByUserIdAndStatus(Long userId, RecommendationRunStatus status);
}

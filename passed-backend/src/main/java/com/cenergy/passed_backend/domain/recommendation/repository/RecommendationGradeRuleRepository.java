package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationGradeRuleRepository extends JpaRepository<RecommendationGradeRule, Long> {
    List<RecommendationGradeRule> findAllByScoringPolicyIdOrderByPriorityDesc(Long scoringPolicyId);
}

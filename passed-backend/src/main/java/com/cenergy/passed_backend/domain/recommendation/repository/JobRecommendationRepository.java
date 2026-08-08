package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
}

package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRecommendationSkillDetailRepository
        extends JpaRepository<JobRecommendationSkillDetail, Long> {
}

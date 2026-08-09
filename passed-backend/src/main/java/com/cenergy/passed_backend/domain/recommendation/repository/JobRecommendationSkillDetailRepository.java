package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRecommendationSkillDetailRepository
        extends JpaRepository<JobRecommendationSkillDetail, Long> {
    @EntityGraph(attributePaths = "skill")
    List<JobRecommendationSkillDetail> findAllByJobRecommendationIdOrderByIdAsc(
            Long jobRecommendationId
    );
}

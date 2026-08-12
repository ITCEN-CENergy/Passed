package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
    Optional<JobRecommendation> findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
            Long jobPostingId,
            Long userId
    );

    @EntityGraph(attributePaths = {
            "jobPosting.company",
            "jobPosting.jobRole.industry"
    })
    List<JobRecommendation> findAllByRecommendationRunIdOrderByRankOrderAsc(Long recommendationRunId);

    @EntityGraph(attributePaths = {
            "recommendationRun",
            "jobPosting.company",
            "jobPosting.jobRole.industry"
    })
    Optional<JobRecommendation> findByIdAndRecommendationRunIdAndRecommendationRunUserId(
            Long id,
            Long recommendationRunId,
            Long userId
    );
}

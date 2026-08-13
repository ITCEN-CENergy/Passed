package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, Long> {
    @Query("""
            select distinct recommendation.jobPosting.id
            from JobRecommendation recommendation
            where recommendation.recommendationRun.user.id = :userId
              and recommendation.jobPosting.id in :jobPostingIds
            """)
    List<Long> findMatchedJobPostingIds(
            @Param("userId") Long userId,
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );

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

package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, Long> {
    boolean existsByUserIdAndStatus(Long userId, RecommendationRunStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from RecommendationRun run where run.id = :id")
    Optional<RecommendationRun> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select run
            from RecommendationRun run
            where run.user.id = :userId
              and (:type is null or run.recommendationType = :type)
              and (:status is null or run.status = :status)
            order by
                case when run.completedAt is null then 1 else 0 end,
                run.completedAt desc,
                run.startedAt desc,
                run.id desc
            """)
    Page<RecommendationRun> findHistoryByUserIdOrderByCompletedAtDesc(
            @Param("userId") Long userId,
            @Param("type") RecommendationRunType type,
            @Param("status") RecommendationRunStatus status,
            Pageable pageable
    );

    @Query(value = """
            select *
            from recommendation_runs
            where user_id = :userId
              and status = 'COMPLETED'
              and recommendation_type = 'MULTIPLE_POSTINGS'
            order by started_at desc, id desc
            limit 1
            """, nativeQuery = true)
    Optional<RecommendationRun> findLatestCompletedPreferenceRun(@Param("userId") Long userId);

    Optional<RecommendationRun> findByIdAndUserId(Long id, Long userId);
}

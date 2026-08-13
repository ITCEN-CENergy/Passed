package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
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

    Page<RecommendationRun> findAllByUserIdOrderByStartedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<RecommendationRun> findByIdAndUserId(Long id, Long userId);
}

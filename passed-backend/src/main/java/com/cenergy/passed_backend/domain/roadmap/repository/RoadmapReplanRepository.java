package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapReplan;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapReplanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface RoadmapReplanRepository extends JpaRepository<RoadmapReplan, Long> {
    Optional<RoadmapReplan> findByTokenAndRoadmapIdAndUserIdAndStatus(
            UUID token, Long roadmapId, Long userId, RoadmapReplanStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RoadmapReplan r
            where r.token = :token and r.roadmapId = :roadmapId and r.userId = :userId
            """)
    Optional<RoadmapReplan> findOwnedForUpdate(@Param("token") UUID token,
                                               @Param("roadmapId") Long roadmapId,
                                               @Param("userId") Long userId);
}

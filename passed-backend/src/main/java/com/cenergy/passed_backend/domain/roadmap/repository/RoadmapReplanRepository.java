package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapReplan;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapReplanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoadmapReplanRepository extends JpaRepository<RoadmapReplan, Long> {
    Optional<RoadmapReplan> findByTokenAndRoadmapIdAndUserIdAndStatus(
            UUID token, Long roadmapId, Long userId, RoadmapReplanStatus status);
}

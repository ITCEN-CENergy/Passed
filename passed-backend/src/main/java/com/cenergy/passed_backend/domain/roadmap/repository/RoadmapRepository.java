package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    List<Roadmap> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Roadmap> findByIdAndUserId(Long roadmapId, Long userId);
}

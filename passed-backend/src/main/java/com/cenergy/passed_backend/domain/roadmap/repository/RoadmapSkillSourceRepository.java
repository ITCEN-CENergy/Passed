package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapSkillSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoadmapSkillSourceRepository extends JpaRepository<RoadmapSkillSource, Long> {
    List<RoadmapSkillSource> findAllByRoadmapSkillIdInOrderByRoadmapSkillIdAscIdAsc(
            Collection<Long> roadmapSkillIds);
}

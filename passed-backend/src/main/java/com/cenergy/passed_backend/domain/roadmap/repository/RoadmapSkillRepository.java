package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.roadmap.entity.RoadmapSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoadmapSkillRepository extends JpaRepository<RoadmapSkill, Long> {
    List<RoadmapSkill> findAllByRoadmapIdOrderByPriorityAscIdAsc(Long roadmapId);

    @Query("""
            select rs.roadmap.id as roadmapId, count(rs.id) as count
            from RoadmapSkill rs
            where rs.roadmap.id in :roadmapIds
            group by rs.roadmap.id
            """)
    List<RoadmapCount> countByRoadmapIds(@Param("roadmapIds") Collection<Long> roadmapIds);
}

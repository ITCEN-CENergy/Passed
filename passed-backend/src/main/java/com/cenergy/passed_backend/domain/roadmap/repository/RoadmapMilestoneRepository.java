package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoadmapMilestoneRepository extends JpaRepository<RoadmapMilestone, Long> {
    @Query("""
            select rm
            from RoadmapMilestone rm
            where rm.roadmapSkill.id in :roadmapSkillIds
            order by rm.roadmapSkill.id asc, rm.learningOrder asc
            """)
    List<RoadmapMilestone> findAllByRoadmapSkillIds(
            @Param("roadmapSkillIds") Collection<Long> roadmapSkillIds);

    @Query("""
            select distinct rm.roadmapSkill.id
            from RoadmapMilestone rm
            where rm.milestone.id = :milestoneId
            """)
    List<Long> findRoadmapSkillIdsByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("""
            select distinct rm.roadmapSkill.roadmap.id
            from RoadmapMilestone rm
            where rm.milestone.id = :milestoneId
            order by rm.roadmapSkill.roadmap.id
            """)
    List<Long> findRoadmapIdsByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("""
            select distinct rm.milestone.id
            from RoadmapMilestone rm
            where rm.roadmapSkill.roadmap.id = :roadmapId
            """)
    List<Long> findMilestoneIdsByRoadmapId(@Param("roadmapId") Long roadmapId);

    @Query("""
            select rm.roadmapSkill.roadmap.id as roadmapId, count(rm.id) as count
            from RoadmapMilestone rm
            where rm.roadmapSkill.roadmap.id in :roadmapIds
            group by rm.roadmapSkill.roadmap.id
            """)
    List<RoadmapCount> countByRoadmapIds(@Param("roadmapIds") Collection<Long> roadmapIds);
}

package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByIdInOrderByIdAsc(Collection<Long> milestoneIds);

    List<Milestone> findAllByUserIdAndStandardCompetencyIdInOrderByIdAsc(
            Long userId, Collection<Long> standardCompetencyIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Milestone m
            where m.id in :milestoneIds
              and m.userId = :userId
              and not exists (
                  select rm.id
                  from RoadmapMilestone rm
                  where rm.milestone = m
              )
            """)
    int deleteUnreferencedByIdsAndUserId(@Param("milestoneIds") Collection<Long> milestoneIds,
                                         @Param("userId") Long userId);
}

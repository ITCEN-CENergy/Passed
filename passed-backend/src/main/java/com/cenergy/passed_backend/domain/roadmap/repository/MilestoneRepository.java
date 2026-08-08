package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByIdInOrderByIdAsc(Collection<Long> milestoneIds);

    List<Milestone> findAllByUserIdAndStandardCompetencyIdInOrderByIdAsc(
            Long userId, Collection<Long> standardCompetencyIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Milestone m where m.id = :milestoneId and m.userId = :userId")
    Optional<Milestone> findOwnedForUpdate(@Param("milestoneId") Long milestoneId,
                                           @Param("userId") Long userId);

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

package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    List<Roadmap> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Roadmap> findByIdAndUserId(Long roadmapId, Long userId);

    @Query("""
            select r
            from Roadmap r
            where r.userId = :userId
              and r.status = :status
              and (select count(rjp.id)
                   from RoadmapJobPosting rjp
                   where rjp.roadmap = r) = :jobPostingCount
              and (select count(matched.id)
                   from RoadmapJobPosting matched
                   where matched.roadmap = r
                     and matched.jobPostingId in :jobPostingIds) = :jobPostingCount
            order by r.id asc
            """)
    List<Roadmap> findAllByUserIdAndStatusAndExactJobPostingIds(
            @Param("userId") Long userId,
            @Param("status") RoadmapStatus status,
            @Param("jobPostingIds") Collection<Long> jobPostingIds,
            @Param("jobPostingCount") long jobPostingCount);
}

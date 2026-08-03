package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.roadmap.entity.RoadmapJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoadmapJobPostingRepository extends JpaRepository<RoadmapJobPosting, Long> {
    List<RoadmapJobPosting> findAllByRoadmapIdOrderByIdAsc(Long roadmapId);

    @Query("""
            select rjp.roadmap.id as roadmapId, count(rjp.id) as count
            from RoadmapJobPosting rjp
            where rjp.roadmap.id in :roadmapIds
            group by rjp.roadmap.id
            """)
    List<RoadmapCount> countByRoadmapIds(@Param("roadmapIds") Collection<Long> roadmapIds);
}

package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.jobposting.entity.JobRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecommendationJobRoleRepository extends JpaRepository<JobRole, Long> {
    @EntityGraph(attributePaths = "industry")
    List<JobRole> findAllByIdIn(Collection<Long> ids);
}

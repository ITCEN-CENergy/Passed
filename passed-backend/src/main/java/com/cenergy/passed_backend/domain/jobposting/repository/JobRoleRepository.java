package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JobRoleRepository extends JpaRepository<JobRole, Long> {
    @EntityGraph(attributePaths = "industry")
    List<JobRole> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "industry")
    List<JobRole> findAllByIndustryIdOrderByIdAsc(Long industryId);
}

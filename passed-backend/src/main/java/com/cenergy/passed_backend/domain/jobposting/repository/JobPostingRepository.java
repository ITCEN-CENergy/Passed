package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("""
            select jobPosting.id
            from JobPosting jobPosting
            where jobPosting.jobRole.id in :jobRoleIds
            order by jobPosting.id
            """)
    List<Long> findCandidateIdsByJobRoleIds(@Param("jobRoleIds") Collection<Long> jobRoleIds);
}

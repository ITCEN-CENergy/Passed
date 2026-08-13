package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JobPostingSkillRepository extends JpaRepository<JobPostingSkill, Long> {

    @EntityGraph(attributePaths = {"jobPosting", "skill"})
    List<JobPostingSkill> findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(
            Collection<Long> jobPostingIds
    );
    @EntityGraph(attributePaths = {"jobPosting", "skill"})
    List<JobPostingSkill> findAllByJobPostingId(Long jobPostingId);
}

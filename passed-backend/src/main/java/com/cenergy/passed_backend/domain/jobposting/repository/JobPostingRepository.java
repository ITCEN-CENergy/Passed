package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 채용공고 엔티티를 조회하는 저장소다.
 * 공고별 자기소개서 생성 시 참조 무결성과 소유 범위의 공고 존재 여부를 검증하는 데 사용한다.
 */
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Override
    @EntityGraph(attributePaths = {"company", "jobRole", "jobRole.industry"})
    Page<JobPosting> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"company", "jobRole", "jobRole.industry"})
    Optional<JobPosting> findById(Long id);

    @EntityGraph(attributePaths = "company")
    List<JobPosting> findAllByIdIn(Collection<Long> ids);

    @Query("""
            select jobPosting.id
            from JobPosting jobPosting
            where jobPosting.jobRole.id in :jobRoleIds
            order by jobPosting.id
            """)
    List<Long> findCandidateIdsByJobRoleIds(@Param("jobRoleIds") Collection<Long> jobRoleIds);
}

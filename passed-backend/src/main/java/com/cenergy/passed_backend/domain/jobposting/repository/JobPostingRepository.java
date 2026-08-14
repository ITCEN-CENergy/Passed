package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;

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

    @EntityGraph(attributePaths = {"company", "jobRole", "jobRole.industry"})
    @Query("""
            select jobPosting
            from JobPosting jobPosting
            where (
                :keyword = ''
                or lower(jobPosting.title) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.positionDetail, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.mainDuty, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.qualification, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.preference, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.disqualifyReason, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.process, '')) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(jobPosting.company.benefits, '')) like lower(concat('%', :keyword, '%'))
            )
            and (:region = '' or jobPosting.region like concat('%', :region, '%'))
            and (:industryId = 0 or jobPosting.jobRole.industry.id = :industryId)
            and (:jobRoleId = 0 or jobPosting.jobRole.id = :jobRoleId)
            and (:companySizeFilter = false or jobPosting.company.companySize = :companySize)
            and (
                :matchedOnly = false
                or exists (
                    select recommendation.id
                    from JobRecommendation recommendation
                    where recommendation.jobPosting = jobPosting
                      and recommendation.recommendationRun.user.id = :userId
                )
            )
            """)
    Page<JobPosting> findFiltered(
            @Param("keyword") String keyword,
            @Param("region") String region,
            @Param("industryId") long industryId,
            @Param("jobRoleId") long jobRoleId,
            @Param("companySizeFilter") boolean companySizeFilter,
            @Param("companySize") CompanySize companySize,
            @Param("matchedOnly") boolean matchedOnly,
            @Param("userId") Long userId,
            Pageable pageable
    );

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

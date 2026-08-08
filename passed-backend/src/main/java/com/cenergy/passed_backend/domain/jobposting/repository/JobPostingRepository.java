package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 채용공고 엔티티를 조회하는 저장소다.
 * 공고별 자기소개서 생성 시 참조 무결성과 소유 범위의 공고 존재 여부를 검증하는 데 사용한다.
 */
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
}

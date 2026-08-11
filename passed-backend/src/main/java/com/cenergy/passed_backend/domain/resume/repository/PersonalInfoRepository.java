package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.PersonalInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalInfoRepository extends JpaRepository<PersonalInfo, Long> {
    Optional<PersonalInfo> findByResumeId(Long resumeId);
}

package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.OverseasExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OverseasExperienceRepository extends JpaRepository<OverseasExperience, Long> {
    List<OverseasExperience> findAllByResumeIdOrderById(Long resumeId);
}

package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.Award;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AwardRepository extends JpaRepository<Award, Long> {
    List<Award> findAllByResumeIdOrderById(Long resumeId);
}

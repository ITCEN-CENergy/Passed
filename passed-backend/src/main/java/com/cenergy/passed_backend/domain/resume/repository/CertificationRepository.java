package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CertificationRepository extends JpaRepository<Certification, Long> {
    List<Certification> findAllByResumeIdOrderById(Long resumeId);
}

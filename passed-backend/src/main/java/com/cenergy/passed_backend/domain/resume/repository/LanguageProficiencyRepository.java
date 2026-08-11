package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.LanguageProficiency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LanguageProficiencyRepository extends JpaRepository<LanguageProficiency, Long> {
    List<LanguageProficiency> findAllByResumeIdOrderById(Long resumeId);
}

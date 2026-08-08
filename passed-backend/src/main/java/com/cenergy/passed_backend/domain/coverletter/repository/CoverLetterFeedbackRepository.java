package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterFeedbackRepository extends JpaRepository<CoverLetterFeedback, Long> {
}
package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterQuestionRepository extends JpaRepository<CoverLetterQuestion, Long> {
}
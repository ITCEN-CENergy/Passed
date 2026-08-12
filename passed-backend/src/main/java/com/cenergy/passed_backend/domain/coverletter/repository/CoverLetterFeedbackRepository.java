package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoverLetterFeedbackRepository extends JpaRepository<CoverLetterFeedback, Long> {
    Optional<CoverLetterFeedback> findByCoverLetterCompanyId(Long coverLetterId);

    long deleteByCoverLetterCompanyId(Long coverLetterId);
}

package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverLetterItemFeedbackRepository extends JpaRepository<CoverLetterItemFeedback, Long> {
    Optional<CoverLetterItemFeedback> findByCoverLetterCompanyItemId(Long itemId);

    Optional<CoverLetterItemFeedback> findByCoverLetterCompanyItemIdAndCoverLetterCompanyItemCoverLetterCompanyUserId(
            Long itemId,
            Long userId
    );
}

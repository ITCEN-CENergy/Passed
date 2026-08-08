package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverLetterItemFeedbackRepository extends JpaRepository<CoverLetterItemFeedback, Long> {
    /** 특정 문항의 기존 첨삭 결과를 삭제해 답변 변경 후 오래된 피드백을 막는다. */
    long deleteByCoverLetterCompanyItemId(Long itemId);

    /** 문항 ID로 첨삭 결과를 찾는다. */
    Optional<CoverLetterItemFeedback> findByCoverLetterCompanyItemId(Long itemId);

    /** 현재 사용자 소유 문항의 첨삭 결과만 조회한다. */
    Optional<CoverLetterItemFeedback> findByCoverLetterCompanyItemIdAndCoverLetterCompanyItemCoverLetterCompanyUserId(
            Long itemId,
            Long userId
    );
}

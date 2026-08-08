package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CoverLetterFeedbackQueryService {
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterItemFeedbackRepository feedbackRepository;
    private final JobPostingDescriptionBuilder jobPostingDescriptionBuilder;

    public CoverLetterFeedbackQueryService(
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterItemFeedbackRepository feedbackRepository,
            JobPostingDescriptionBuilder jobPostingDescriptionBuilder
    ) {
        this.itemRepository = itemRepository;
        this.feedbackRepository = feedbackRepository;
        this.jobPostingDescriptionBuilder = jobPostingDescriptionBuilder;
    }

    public CoverLetterFeedbackInput loadInput(Long userId, Long itemId) {
        CoverLetterCompanyItem item = itemRepository.findOwnedItem(itemId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_NOT_FOUND,
                        "Cover letter item not found"
                ));
        if (item.getAnswer() == null || item.getAnswer().isBlank()) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_ITEM_ANSWER_REQUIRED,
                    "Cover letter item answer is required"
            );
        }
        return new CoverLetterFeedbackInput(
                item.getId(),
                item.getQuestionText(),
                item.getAnswer(),
                item.getCharacterLimit(),
                jobPostingDescriptionBuilder.build(item.getCoverLetterCompany().getJobPosting())
        );
    }

    public CoverLetterFeedbackResult findFeedback(Long userId, Long itemId) {
        CoverLetterItemFeedback feedback = feedbackRepository
                .findByCoverLetterCompanyItemIdAndCoverLetterCompanyItemCoverLetterCompanyUserId(itemId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND,
                        "Cover letter item feedback not found"
                ));
        return toResult(feedback);
    }

    static CoverLetterFeedbackResult toResult(CoverLetterItemFeedback feedback) {
        CoverLetterCompanyItem item = feedback.getCoverLetterCompanyItem();
        String suggestedAnswer = feedback.getSuggestedAnswer();
        int length = suggestedAnswer == null ? 0 : suggestedAnswer.length();
        Integer limit = item.getCharacterLimit();
        return new CoverLetterFeedbackResult(
                feedback.getId(),
                item.getId(),
                feedback.getScore(),
                feedback.getStrengths(),
                feedback.getImprovements(),
                suggestedAnswer,
                limit,
                length,
                limit == null || length <= limit,
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }
}

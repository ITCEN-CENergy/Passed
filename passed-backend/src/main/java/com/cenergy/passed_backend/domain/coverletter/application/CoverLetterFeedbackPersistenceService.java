package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CoverLetterFeedbackPersistenceService {
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterItemFeedbackRepository feedbackRepository;
    private final JobPostingDescriptionBuilder jobPostingDescriptionBuilder;

    public CoverLetterFeedbackPersistenceService(
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterItemFeedbackRepository feedbackRepository,
            JobPostingDescriptionBuilder jobPostingDescriptionBuilder
    ) {
        this.itemRepository = itemRepository;
        this.feedbackRepository = feedbackRepository;
        this.jobPostingDescriptionBuilder = jobPostingDescriptionBuilder;
    }

    @Transactional
    public CoverLetterFeedbackResult save(
            Long userId,
            CoverLetterFeedbackInput input,
            CoverLetterScore score,
            ValidatedCoverLetterAiResult aiResult
    ) {
        CoverLetterCompanyItem item = itemRepository.findOwnedItemForUpdate(input.itemId(), userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_NOT_FOUND,
                        "Cover letter item not found"
                ));
        if (!Objects.equals(item.getQuestionText(), input.question())
                || !Objects.equals(item.getAnswer(), input.answer())
                || !Objects.equals(
                        jobPostingDescriptionBuilder.build(item.getCoverLetterCompany()),
                        input.jobDescription()
                )) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_ITEM_CHANGED,
                    "Cover letter item changed while feedback was generated"
            );
        }

        String improvements = improvements(aiResult);
        CoverLetterItemFeedback feedback = feedbackRepository.findByCoverLetterCompanyItemId(item.getId())
                .map(existing -> {
                    existing.update(score, null, improvements, null);
                    return existing;
                })
                .orElseGet(() -> CoverLetterItemFeedback.create(
                        item,
                        score,
                        null,
                        improvements,
                        null
                ));
        return CoverLetterFeedbackQueryService.toResult(feedbackRepository.saveAndFlush(feedback));
    }

    @Transactional
    public CoverLetterFeedbackResult saveSuggestedAnswer(
            Long userId,
            CoverLetterFeedbackInput input,
            String suggestedAnswer
    ) {
        CoverLetterCompanyItem item = itemRepository.findOwnedItemForUpdate(input.itemId(), userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_NOT_FOUND,
                        "Cover letter item not found"
                ));
        if (!Objects.equals(item.getQuestionText(), input.question())
                || !Objects.equals(item.getAnswer(), input.answer())
                || !Objects.equals(
                        jobPostingDescriptionBuilder.build(item.getCoverLetterCompany()),
                        input.jobDescription()
                )) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_ITEM_CHANGED,
                    "Cover letter item changed while suggested answer was generated"
            );
        }
        CoverLetterItemFeedback feedback = feedbackRepository.findByCoverLetterCompanyItemId(item.getId())
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND,
                        "Cover letter item feedback not found"
                ));
        feedback.update(
                feedback.getScore(),
                feedback.getStrengths(),
                feedback.getImprovements(),
                suggestedAnswer.trim()
        );
        return CoverLetterFeedbackQueryService.toResult(feedbackRepository.saveAndFlush(feedback));
    }

    private String improvements(ValidatedCoverLetterAiResult result) {
        return "[질문-답변 일치도]\n"
                + result.qaAlignmentFeedback().trim()
                + "\n\n[채용공고 적합도]\n"
                + result.jobFitFeedback().trim();
    }
}

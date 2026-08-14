package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.exception.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterItemFeedbackResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterOverallFeedbackResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterFeedback;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterFeedbackRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CoverLetterOverallFeedbackPersistenceService {
    private final CoverLetterCompanyRepository coverLetterRepository;
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterFeedbackRepository overallRepository;
    private final CoverLetterItemFeedbackRepository itemFeedbackRepository;
    private final JobPostingDescriptionBuilder descriptionBuilder;
    private final CoverLetterScorePolicy scorePolicy;

    public CoverLetterOverallFeedbackPersistenceService(
            CoverLetterCompanyRepository coverLetterRepository,
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterFeedbackRepository overallRepository,
            CoverLetterItemFeedbackRepository itemFeedbackRepository,
            JobPostingDescriptionBuilder descriptionBuilder,
            CoverLetterScorePolicy scorePolicy
    ) {
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
        this.overallRepository = overallRepository;
        this.itemFeedbackRepository = itemFeedbackRepository;
        this.descriptionBuilder = descriptionBuilder;
        this.scorePolicy = scorePolicy;
    }

    @Transactional
    public CoverLetterOverallFeedbackResponse save(
            Long userId,
            Long coverLetterId,
            CoverLetterReviewAiRequest input,
            CoverLetterReviewAiResponse aiResponse
    ) {
        CoverLetterCompany coverLetter = coverLetterRepository.findOwnedForUpdate(coverLetterId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_NOT_FOUND, "Cover letter not found"));
        List<CoverLetterCompanyItem> currentItems = answeredItems(coverLetterId);
        validateUnchanged(coverLetter, currentItems, input);

        Map<Long, CoverLetterCompanyItem> itemById = currentItems.stream()
                .collect(Collectors.toMap(CoverLetterCompanyItem::getId, Function.identity()));
        List<CoverLetterItemFeedbackResponse> itemResponses = aiResponse.items().stream()
                .map(result -> saveItem(itemById, result))
                .map(CoverLetterFeedbackQueryService::toResult)
                .map(CoverLetterItemFeedbackResponse::from)
                .toList();
        if (!itemById.isEmpty()) invalidResponse("review response omitted an item");

        CoverLetterReviewAiResponse.OverallFeedback overall = aiResponse.overallFeedback();
        requireText(overall.summary(), "summary");
        requireText(overall.strengths(), "strengths");
        requireText(overall.improvements(), "improvements");
        CoverLetterFeedback feedback = overallRepository.findByCoverLetterCompanyId(coverLetterId)
                .map(existing -> {
                    existing.update(score(overall.overallScore()), overall.summary(),
                            overall.strengths(), overall.improvements(), "gpt-4o-mini");
                    return existing;
                })
                .orElseGet(() -> CoverLetterFeedback.create(
                        coverLetter, score(overall.overallScore()), overall.summary(),
                        overall.strengths(), overall.improvements(), "gpt-4o-mini"));
        return CoverLetterOverallFeedbackResponse.from(
                overallRepository.saveAndFlush(feedback), itemResponses);
    }

    private CoverLetterItemFeedback saveItem(
            Map<Long, CoverLetterCompanyItem> itemById,
            CoverLetterReviewAiResponse.ItemFeedback result
    ) {
        CoverLetterCompanyItem item = itemById.remove(result.itemId());
        if (item == null || result.displayOrder() == null
                || result.displayOrder() != item.getDisplayOrder()) {
            invalidResponse("review item does not match requested item");
        }
        requireText(result.qaAlignmentFeedback(), "qa_alignment_feedback");
        requireText(result.jobFitFeedback(), "jd_fit_feedback");
        requireText(result.finalEditedContent(), "final_edited_content");
        String improvements = "[질문-답변 일치도]\n" + result.qaAlignmentFeedback().trim()
                + "\n\n[채용공고 적합도]\n" + result.jobFitFeedback().trim();
        CoverLetterItemFeedback feedback = itemFeedbackRepository
                .findByCoverLetterCompanyItemId(item.getId())
                .map(existing -> {
                    existing.update(score(result.qaAlignmentScore()), null,
                            improvements, result.finalEditedContent());
                    return existing;
                })
                .orElseGet(() -> CoverLetterItemFeedback.create(
                        item, score(result.qaAlignmentScore()), null,
                        improvements, result.finalEditedContent()));
        return itemFeedbackRepository.save(feedback);
    }

    private List<CoverLetterCompanyItem> answeredItems(Long coverLetterId) {
        return itemRepository.findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(coverLetterId)
                .stream()
                .filter(item -> item.getAnswer() != null && !item.getAnswer().isBlank())
                .toList();
    }

    private void validateUnchanged(
            CoverLetterCompany coverLetter,
            List<CoverLetterCompanyItem> currentItems,
            CoverLetterReviewAiRequest input
    ) {
        if (!Objects.equals(descriptionBuilder.build(coverLetter), input.jobDescription())
                || currentItems.size() != input.items().size()) changed();
        for (int index = 0; index < currentItems.size(); index++) {
            CoverLetterCompanyItem current = currentItems.get(index);
            CoverLetterReviewAiRequest.Item original = input.items().get(index);
            if (!Objects.equals(current.getId(), original.itemId())
                    || current.getDisplayOrder() != original.displayOrder()
                    || !Objects.equals(current.getQuestionText(), original.question())
                    || !Objects.equals(current.getAnswer(), original.content())
                    || !Objects.equals(current.getCharacterLimit(), original.characterLimit())) changed();
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) invalidResponse(field + " must not be blank");
    }

    private com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore score(Integer value) {
        if (value == null) invalidResponse("score must not be null");
        return scorePolicy.from(value);
    }

    private void changed() {
        throw new CoverLetterException(ErrorCode.COVER_LETTER_ITEM_CHANGED,
                "Cover letter changed while overall feedback was generated");
    }

    private void invalidResponse(String message) {
        throw new CoverLetterAiException(ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE, message);
    }
}

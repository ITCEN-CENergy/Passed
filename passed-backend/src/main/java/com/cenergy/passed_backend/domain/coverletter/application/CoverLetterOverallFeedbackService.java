package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiResponse;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterUserSkill;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterItemFeedbackResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterOverallFeedbackResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterFeedback;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterFeedbackRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CoverLetterOverallFeedbackService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CoverLetterCompanyRepository coverLetterRepository;
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterFeedbackRepository overallRepository;
    private final CoverLetterItemFeedbackRepository itemFeedbackRepository;
    private final JobPostingDescriptionBuilder descriptionBuilder;
    private final CoverLetterAiClient aiClient;
    private final CoverLetterOverallFeedbackPersistenceService persistenceService;
    private final UserSkillRepository userSkillRepository;

    public CoverLetterOverallFeedbackService(
            CurrentUserIdProvider currentUserIdProvider,
            CoverLetterCompanyRepository coverLetterRepository,
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterFeedbackRepository overallRepository,
            CoverLetterItemFeedbackRepository itemFeedbackRepository,
            JobPostingDescriptionBuilder descriptionBuilder,
            CoverLetterAiClient aiClient,
            CoverLetterOverallFeedbackPersistenceService persistenceService,
            UserSkillRepository userSkillRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
        this.overallRepository = overallRepository;
        this.itemFeedbackRepository = itemFeedbackRepository;
        this.descriptionBuilder = descriptionBuilder;
        this.aiClient = aiClient;
        this.persistenceService = persistenceService;
        this.userSkillRepository = userSkillRepository;
    }

    public CoverLetterOverallFeedbackResponse generate(Long coverLetterId) {
        Long userId = currentUserId();
        CoverLetterCompany coverLetter = ownedCoverLetter(coverLetterId, userId);
        List<CoverLetterReviewAiRequest.Item> items = itemRepository
                .findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(coverLetterId).stream()
                .filter(item -> item.getAnswer() != null && !item.getAnswer().isBlank())
                .map(this::toRequestItem)
                .toList();
        if (items.isEmpty()) {
            throw new CoverLetterException(ErrorCode.COVER_LETTER_ITEM_ANSWER_REQUIRED,
                    "At least one answered item is required");
        }
        CoverLetterReviewAiRequest request = new CoverLetterReviewAiRequest(
                items,
                descriptionBuilder.build(coverLetter),
                userSkillRepository.findAllByUserIdOrderBySkill_IdAsc(userId).stream()
                        .map(CoverLetterUserSkill::from)
                        .toList()
        );
        CoverLetterReviewAiResponse response = aiClient.review(request);
        return persistenceService.save(userId, coverLetterId, request, response);
    }

    @Transactional(readOnly = true)
    public CoverLetterOverallFeedbackResponse find(Long coverLetterId) {
        Long userId = currentUserId();
        ownedCoverLetter(coverLetterId, userId);
        CoverLetterFeedback overall = overallRepository.findByCoverLetterCompanyId(coverLetterId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_FEEDBACK_NOT_FOUND, "Overall feedback not found"));
        List<CoverLetterItemFeedbackResponse> items = itemFeedbackRepository
                .findAllByCoverLetterCompanyItemCoverLetterCompanyIdOrderByCoverLetterCompanyItemDisplayOrderAsc(
                        coverLetterId)
                .stream()
                .map(CoverLetterFeedbackQueryService::toResult)
                .map(CoverLetterItemFeedbackResponse::from)
                .toList();
        return CoverLetterOverallFeedbackResponse.from(overall, items);
    }

    private CoverLetterReviewAiRequest.Item toRequestItem(CoverLetterCompanyItem item) {
        return new CoverLetterReviewAiRequest.Item(
                item.getId(), item.getDisplayOrder(), item.getQuestionText(), item.getAnswer(),
                item.getCharacterLimit());
    }

    private CoverLetterCompany ownedCoverLetter(Long coverLetterId, Long userId) {
        if (coverLetterId == null || coverLetterId <= 0) {
            throw new CoverLetterException(ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Invalid cover letter id");
        }
        return coverLetterRepository.findOwnedDetail(coverLetterId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_NOT_FOUND, "Cover letter not found"));
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new CoverLetterException(ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Invalid current user");
        }
        return userId;
    }
}

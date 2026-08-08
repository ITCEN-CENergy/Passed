package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class CoverLetterFeedbackService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CoverLetterFeedbackQueryService queryService;
    private final CoverLetterFeedbackPersistenceService persistenceService;
    private final CoverLetterAiClient aiClient;
    private final CoverLetterScorePolicy scorePolicy;

    public CoverLetterFeedbackService(
            CurrentUserIdProvider currentUserIdProvider,
            CoverLetterFeedbackQueryService queryService,
            CoverLetterFeedbackPersistenceService persistenceService,
            CoverLetterAiClient aiClient,
            CoverLetterScorePolicy scorePolicy
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.queryService = queryService;
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.scorePolicy = scorePolicy;
    }

    public CoverLetterFeedbackResult generate(Long itemId) {
        Long userId = currentUserId();
        validateItemId(itemId);
        CoverLetterFeedbackInput input = queryService.loadInput(userId, itemId);
        ValidatedCoverLetterAiResult aiResult = aiClient.edit(new CoverLetterAiRequest(
                input.question(),
                input.answer(),
                input.jobDescription()
        ));
        CoverLetterScore score = scorePolicy.from(aiResult.qaAlignmentScore());
        return persistenceService.save(userId, input, score, aiResult);
    }

    public CoverLetterFeedbackResult find(Long itemId) {
        Long userId = currentUserId();
        validateItemId(itemId);
        return queryService.findFeedback(userId, itemId);
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Invalid current user"
            );
        }
        return userId;
    }

    private void validateItemId(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Invalid cover letter item id"
            );
        }
    }
}

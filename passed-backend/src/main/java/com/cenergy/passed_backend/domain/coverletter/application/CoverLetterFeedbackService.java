package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterUserSkill;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CoverLetterFeedbackService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CoverLetterFeedbackQueryService queryService;
    private final CoverLetterFeedbackPersistenceService persistenceService;
    private final CoverLetterAiClient aiClient;
    private final CoverLetterScorePolicy scorePolicy;
    private final UserSkillRepository userSkillRepository;

    public CoverLetterFeedbackService(
            CurrentUserIdProvider currentUserIdProvider,
            CoverLetterFeedbackQueryService queryService,
            CoverLetterFeedbackPersistenceService persistenceService,
            CoverLetterAiClient aiClient,
            CoverLetterScorePolicy scorePolicy,
            UserSkillRepository userSkillRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.queryService = queryService;
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.scorePolicy = scorePolicy;
        this.userSkillRepository = userSkillRepository;
    }

    public CoverLetterFeedbackResult generate(Long itemId) {
        Long userId = currentUserId();
        validateItemId(itemId);
        CoverLetterFeedbackInput input = queryService.loadInput(userId, itemId);
        ValidatedCoverLetterAiResult aiResult = aiClient.edit(new CoverLetterAiRequest(
                input.question(),
                input.answer(),
                input.jobDescription(),
                loadUserSkills(userId)
        ));
        CoverLetterScore score = scorePolicy.from(aiResult.qaAlignmentScore());
        return persistenceService.save(userId, input, score, aiResult);
    }

    public CoverLetterFeedbackResult find(Long itemId) {
        Long userId = currentUserId();
        validateItemId(itemId);
        return queryService.findFeedback(userId, itemId);
    }

    public CoverLetterFeedbackResult generateSuggestedAnswer(Long itemId) {
        Long userId = currentUserId();
        validateItemId(itemId);
        CoverLetterFeedbackInput input = queryService.loadInput(userId, itemId);
        queryService.findFeedback(userId, itemId);
        String suggestedAnswer = aiClient.suggest(new CoverLetterAiRequest(
                input.question(),
                input.answer(),
                input.jobDescription(),
                loadUserSkills(userId)
        ));
        return persistenceService.saveSuggestedAnswer(userId, input, suggestedAnswer);
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

    private List<CoverLetterUserSkill> loadUserSkills(Long userId) {
        return userSkillRepository.findAllByUserIdOrderBySkill_IdAsc(userId).stream()
                .map(CoverLetterUserSkill::from)
                .toList();
    }
}

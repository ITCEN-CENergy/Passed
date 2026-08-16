package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies orchestration rules for the existing item-feedback API.
 */
class CoverLetterFeedbackServiceTest {
    /** The dependencies are mocked so feedback orchestration is tested without external AI or a database. */
    private final CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
    private final CoverLetterFeedbackQueryService queryService = mock(CoverLetterFeedbackQueryService.class);
    private final CoverLetterFeedbackPersistenceService persistenceService =
            mock(CoverLetterFeedbackPersistenceService.class);
    private final CoverLetterAiClient aiClient = mock(CoverLetterAiClient.class);
    private final CoverLetterFeedbackService service = new CoverLetterFeedbackService(
            currentUserIdProvider,
            queryService,
            persistenceService,
            aiClient,
            new CoverLetterScorePolicy()
    );

    /** A valid AI result is scored and persisted for the authenticated user's target item. */
    @Test
    void generatesAndPersistsItemFeedback() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "question", "answer", 700, "job description"
        );
        ValidatedCoverLetterAiResult aiResult = new ValidatedCoverLetterAiResult(
                84, "item feedback", "job feedback"
        );
        CoverLetterFeedbackResult saved = new CoverLetterFeedbackResult(
                33L, 12L, CoverLetterScore.SUFFICIENT, null, "improvement",
                "final answer", 700, 12, true, null, null
        );
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(queryService.loadInput(257L, 12L)).thenReturn(input);
        when(aiClient.edit(new CoverLetterAiRequest("question", "answer", "job description"))).thenReturn(aiResult);
        when(persistenceService.save(257L, input, CoverLetterScore.SUFFICIENT, aiResult)).thenReturn(saved);

        CoverLetterFeedbackResult result = service.generate(12L);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void generatesSuggestedAnswerOnlyAfterFeedbackExists() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "question", "answer", 700, "job description"
        );
        CoverLetterFeedbackResult existing = new CoverLetterFeedbackResult(
                33L, 12L, CoverLetterScore.SUFFICIENT, null, "improvement",
                null, 700, 0, true, null, null
        );
        CoverLetterFeedbackResult saved = new CoverLetterFeedbackResult(
                33L, 12L, CoverLetterScore.SUFFICIENT, null, "improvement",
                "suggested answer", 700, 16, true, null, null
        );
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(queryService.loadInput(257L, 12L)).thenReturn(input);
        when(queryService.findFeedback(257L, 12L)).thenReturn(existing);
        when(aiClient.suggest(new CoverLetterAiRequest("question", "answer", "job description")))
                .thenReturn("suggested answer");
        when(persistenceService.saveSuggestedAnswer(257L, input, "suggested answer"))
                .thenReturn(saved);

        assertThat(service.generateSuggestedAnswer(12L)).isEqualTo(saved);
    }

    /** Invalid item IDs are rejected before repository or external-AI work begins. */
    @Test
    void rejectsInvalidItemBeforeQueryAndAiCall() {
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);

        assertThatThrownBy(() -> service.generate(0L))
                .isInstanceOf(CoverLetterException.class);
        verify(queryService, never()).loadInput(any(), any());
        verify(aiClient, never()).edit(any());
    }

    /** Missing current-user context is rejected before any cover-letter data is loaded. */
    @Test
    void rejectsInvalidCurrentUser() {
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> service.generate(12L))
                .isInstanceOf(CoverLetterException.class);
        verify(queryService, never()).loadInput(any(), any());
    }
}

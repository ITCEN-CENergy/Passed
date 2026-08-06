package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoverLetterFeedbackServiceTest {
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

    @Test
    void generatesAndPersistsItemFeedback() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "질문", "답변", 700, "공고"
        );
        ValidatedCoverLetterAiResult aiResult = new ValidatedCoverLetterAiResult(
                "교정 답변", 84, "문항 피드백", "직무 피드백", "수정 답변"
        );
        CoverLetterFeedbackResult saved = new CoverLetterFeedbackResult(
                33L, 12L, CoverLetterScore.SUFFICIENT, null, "개선점",
                "수정 답변", 700, 5, true, null, null
        );
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(queryService.loadInput(257L, 12L)).thenReturn(input);
        when(aiClient.edit(new CoverLetterAiRequest("질문", "답변", "공고"))).thenReturn(aiResult);
        when(persistenceService.save(257L, input, CoverLetterScore.SUFFICIENT, aiResult)).thenReturn(saved);

        CoverLetterFeedbackResult result = service.generate(12L);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void rejectsInvalidItemBeforeQueryAndAiCall() {
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);

        assertThatThrownBy(() -> service.generate(0L))
                .isInstanceOf(CoverLetterException.class);
        verify(queryService, never()).loadInput(any(), any());
        verify(aiClient, never()).edit(any());
    }

    @Test
    void rejectsInvalidCurrentUser() {
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> service.generate(12L))
                .isInstanceOf(CoverLetterException.class);
        verify(queryService, never()).loadInput(any(), any());
    }
}

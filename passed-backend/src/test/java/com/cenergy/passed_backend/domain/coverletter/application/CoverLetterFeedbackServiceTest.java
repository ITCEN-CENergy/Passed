package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterUserSkill;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
    private final UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
    private final CoverLetterFeedbackService service = new CoverLetterFeedbackService(
            currentUserIdProvider,
            queryService,
            persistenceService,
            aiClient,
            new CoverLetterScorePolicy(),
            userSkillRepository
    );

    /** A valid AI result is scored and persisted for the authenticated user's target item. */
    @Test
    void generatesAndPersistsItemFeedback() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "question", "answer", 700, "job description", "도전과 협업"
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
        when(userSkillRepository.findAllByUserIdOrderBySkill_IdAsc(257L))
                .thenReturn(List.of(userSkill(9L, "Spring Boot", SkillCategory.TECHNICAL_SKILL, (short) 2)));
        when(aiClient.edit(new CoverLetterAiRequest(
                "question",
                "answer",
                "job description",
                "도전과 협업",
                List.of(new CoverLetterUserSkill(9L, "Spring Boot", SkillCategory.TECHNICAL_SKILL, (short) 2)),
                700
        ))).thenReturn(aiResult);
        when(persistenceService.save(257L, input, CoverLetterScore.SUFFICIENT, aiResult)).thenReturn(saved);

        CoverLetterFeedbackResult result = service.generate(12L);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void generatesSuggestedAnswerOnlyAfterFeedbackExists() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "question", "answer", 700, "job description", "도전과 협업"
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
        when(aiClient.suggest(new CoverLetterAiRequest(
                "question", "answer", "job description", "도전과 협업", List.of(), 700
        )))
                .thenReturn("suggested answer");
        when(persistenceService.saveSuggestedAnswer(257L, input, "suggested answer"))
                .thenReturn(saved);

        assertThat(service.generateSuggestedAnswer(12L)).isEqualTo(saved);
    }

    @Test
    void truncatesSuggestedAnswerToCharacterLimitBeforeSaving() {
        CoverLetterFeedbackInput input = new CoverLetterFeedbackInput(
                12L, "question", "answer", 5, "job description", null
        );
        CoverLetterFeedbackResult existing = new CoverLetterFeedbackResult(
                33L, 12L, CoverLetterScore.SUFFICIENT, null, "improvement",
                null, 5, 0, true, null, null
        );
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(queryService.loadInput(257L, 12L)).thenReturn(input);
        when(queryService.findFeedback(257L, 12L)).thenReturn(existing);
        when(aiClient.suggest(new CoverLetterAiRequest(
                "question", "answer", "job description", null, List.of(), 5
        ))).thenReturn("가나다라마바사");

        service.generateSuggestedAnswer(12L);

        verify(persistenceService).saveSuggestedAnswer(257L, input, "가나다라마");
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

    private UserSkill userSkill(Long skillId, String name, SkillCategory category, short level) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(skillId);
        when(skill.getName()).thenReturn(name);
        when(skill.getCategory()).thenReturn(category);
        UserSkill userSkill = mock(UserSkill.class);
        when(userSkill.getSkill()).thenReturn(skill);
        when(userSkill.getSkillLevel()).thenReturn(level);
        return userSkill;
    }
}

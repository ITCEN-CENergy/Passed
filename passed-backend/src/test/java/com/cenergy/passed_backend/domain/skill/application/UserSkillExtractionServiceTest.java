package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.ai.client.UserSkillAiClient;
import com.cenergy.passed_backend.domain.skill.ai.dto.UserSkillAiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSkillExtractionServiceTest {

    @Test
    void startsAnalysisForTheCurrentUserOnly() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        UserSkillAiClient client = mock(UserSkillAiClient.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(client.extract(257L)).thenReturn(new UserSkillAiResponse(
                257L, 7, 5, 1, true, 3, 2
        ));
        UserSkillExtractionService service = new UserSkillExtractionService(
                currentUserIdProvider,
                client
        );

        var response = service.extract();

        assertThat(response.skillCount()).isEqualTo(5);
        verify(client).extract(257L);
    }
}

package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.ai.client.LearningCompetencyAiClient;
import com.cenergy.passed_backend.domain.skillgap.application.AiLearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiLearningCompetencyServiceTest {

    @Test
    void delegatesSinglePostingComparisonToAi() {
        LearningCompetencyAiClient client = mock(LearningCompetencyAiClient.class);
        LearningCompetencyResponse expected = new LearningCompetencyResponse(257L, 101L, List.of());
        when(client.getLearningCompetencies(101L, 257L)).thenReturn(expected);

        var service = new AiLearningCompetencyService(client);

        assertThat(service.getLearningCompetencies(101L, 257L)).isSameAs(expected);
        verify(client).getLearningCompetencies(101L, 257L);
    }
}

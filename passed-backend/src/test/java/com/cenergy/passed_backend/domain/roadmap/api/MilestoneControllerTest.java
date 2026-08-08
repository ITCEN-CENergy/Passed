package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.LearningProgressService;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MilestoneControllerTest {
    @Test
    void changesMilestoneCompletion() {
        LearningProgressService service = mock(LearningProgressService.class);
        MilestoneCompletionRequest request = new MilestoneCompletionRequest(true);
        MilestoneCompletionResponse response = new MilestoneCompletionResponse(
                10L, true, BigDecimal.ZERO, BigDecimal.valueOf(100),
                MilestoneStatus.COMPLETED, null);
        when(service.changeCompletion(10L, request)).thenReturn(response);

        var actual = new MilestoneController(service).changeCompletion(10L, request);

        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actual.getBody()).isEqualTo(response);
        verify(service).changeCompletion(10L, request);
    }
}

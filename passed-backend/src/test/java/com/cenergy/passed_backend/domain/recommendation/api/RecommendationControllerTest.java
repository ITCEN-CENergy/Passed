package com.cenergy.passed_backend.domain.recommendation.api;

import com.cenergy.passed_backend.domain.recommendation.application.RecommendationPreparationService;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationControllerTest {
    @Test
    void returnsCreatedRecommendationRun() {
        RecommendationPreparationService service = mock(RecommendationPreparationService.class);
        RecommendationPrepareRequest request = new RecommendationPrepareRequest(
                2L,
                8L,
                List.of(239L, 237L, 227L)
        );
        RecommendationPrepareResponse response = new RecommendationPrepareResponse(
                10L,
                RecommendationRunStatus.PROCESSING,
                "SKILL_MATCH",
                "v1",
                4,
                15,
                5,
                "a".repeat(64),
                8L,
                List.of(227L, 237L, 239L)
        );
        when(service.prepare(request)).thenReturn(response);

        var actual = new RecommendationController(service).prepare(request);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }
}

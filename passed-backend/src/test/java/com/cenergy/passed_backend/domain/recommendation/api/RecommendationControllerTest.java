package com.cenergy.passed_backend.domain.recommendation.api;

import com.cenergy.passed_backend.domain.recommendation.application.RecommendationPreparationService;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationQueryService;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationControllerTest {
    @Test
    void returnsCreatedRecommendationRun() {
        RecommendationPreparationService service = mock(RecommendationPreparationService.class);
        RecommendationCreateRequest request = new RecommendationCreateRequest(
                2L,
                8L,
                List.of(239L, 237L, 227L)
        );
        RecommendationCreateResponse response = new RecommendationCreateResponse(
                10L,
                RecommendationRunStatus.COMPLETED,
                120,
                48,
                8L,
                List.of(227L, 237L, 239L),
                OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
        );
        when(service.prepare(request)).thenReturn(response);

        var actual = new RecommendationController(
                service,
                mock(RecommendationQueryService.class)
        ).create(request);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }
}

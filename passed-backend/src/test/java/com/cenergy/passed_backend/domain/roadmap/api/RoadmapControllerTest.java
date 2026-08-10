package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapCommandService;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapQueryService;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapReplanService;
import com.cenergy.passed_backend.domain.roadmap.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoadmapControllerTest {
    @Test
    void returnsOkWithGeneratedRoadmap() {
        RoadmapCommandService service = mock(RoadmapCommandService.class);
        RoadmapGenerateRequest request = new RoadmapGenerateRequest(List.of(101L));
        RoadmapGenerateResponse response = new RoadmapGenerateResponse("title", List.of());
        when(service.generate(request)).thenReturn(response);

        var actual = controller(service).generate(request);

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }

    @Test
    void returnsNoContentAfterDeletingRoadmap() {
        RoadmapCommandService service = mock(RoadmapCommandService.class);

        var actual = controller(service).delete(10L);

        assertEquals(HttpStatus.NO_CONTENT, actual.getStatusCode());
        verify(service).delete(10L);
    }

    @Test
    void returnsReplanPreviewWithoutApplyingIt() {
        RoadmapReplanService replanService = mock(RoadmapReplanService.class);
        RoadmapReplanPreviewRequest request = new RoadmapReplanPreviewRequest("실습 중심으로 줄여줘");
        RoadmapReplanPreviewResponse response = new RoadmapReplanPreviewResponse(
                10L, UUID.randomUUID(), "summary", 600, 420,
                java.time.LocalDate.of(2026, 8, 20), java.time.LocalDate.of(2026, 8, 17), List.of());
        when(replanService.preview(10L, request)).thenReturn(response);

        var actual = new RoadmapController(mock(RoadmapCommandService.class),
                mock(RoadmapQueryService.class), replanService).previewReplan(10L, request);

        assertEquals(response, actual.getBody());
        verify(replanService).preview(10L, request);
    }

    @Test
    void appliesApprovedReplanToken() {
        RoadmapReplanService replanService = mock(RoadmapReplanService.class);
        RoadmapReplanApplyRequest request = new RoadmapReplanApplyRequest(UUID.randomUUID());
        RoadmapReplanApplyResponse response = new RoadmapReplanApplyResponse(
                10L, 420, java.time.LocalDate.of(2026, 8, 17));
        when(replanService.apply(10L, request)).thenReturn(response);

        var actual = new RoadmapController(mock(RoadmapCommandService.class),
                mock(RoadmapQueryService.class), replanService).applyReplan(10L, request);

        assertEquals(response, actual.getBody());
        verify(replanService).apply(10L, request);
    }

    private RoadmapController controller(RoadmapCommandService service) {
        return new RoadmapController(service, mock(RoadmapQueryService.class),
                mock(RoadmapReplanService.class));
    }
}

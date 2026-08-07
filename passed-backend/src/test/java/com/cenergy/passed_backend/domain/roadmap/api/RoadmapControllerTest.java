package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapCommandService;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

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

        var actual = new RoadmapController(service, mock(RoadmapQueryService.class)).generate(request);

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }

    @Test
    void returnsNoContentAfterDeletingRoadmap() {
        RoadmapCommandService service = mock(RoadmapCommandService.class);

        var actual = new RoadmapController(service, mock(RoadmapQueryService.class)).delete(10L);

        assertEquals(HttpStatus.NO_CONTENT, actual.getStatusCode());
        verify(service).delete(10L);
    }
}

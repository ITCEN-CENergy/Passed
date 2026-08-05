package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;

import static org.mockito.Mockito.*;

class RoadmapCommandServiceTest {
    @Test
    void usesProviderAndRemovesDuplicatesInFirstSeenOrder() {
        CurrentUserIdProvider provider = () -> 1L;
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(anyLong(), anyList()))
                .thenReturn(new RoadmapGenerationResult("title", List.of()));
        RoadmapPersistenceService persistence = mock(RoadmapPersistenceService.class);
        Roadmap roadmap = Roadmap.create(1L);
        when(persistence.save(anyLong(), anyList(), any())).thenReturn(roadmap);
        RoadmapCommandService service = new RoadmapCommandService(provider, generation, persistence);

        service.generate(new RoadmapGenerateRequest(List.of(102L, 101L, 102L)));

        verify(generation).generate(1L, List.of(102L, 101L));
        verify(persistence).save(eq(1L), eq(List.of(102L, 101L)), any());
    }
}

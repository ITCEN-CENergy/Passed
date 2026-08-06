package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        RoadmapCommandService service = service(provider, generation, persistence,
                mock(RoadmapRepository.class), mock(RoadmapMilestoneRepository.class),
                mock(MilestoneRepository.class));

        service.generate(new RoadmapGenerateRequest(List.of(102L, 101L, 102L)));

        verify(generation).generate(1L, List.of(102L, 101L));
        verify(persistence).save(eq(1L), eq(List.of(102L, 101L)), any());
    }

    @Test
    void deletesRoadmapAndOnlyThenDeletesUnreferencedCandidateMilestones() {
        CurrentUserIdProvider provider = () -> 1L;
        RoadmapRepository roadmapRepository = mock(RoadmapRepository.class);
        RoadmapMilestoneRepository linkRepository = mock(RoadmapMilestoneRepository.class);
        MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
        Roadmap roadmap = Roadmap.create(1L);
        when(roadmapRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(roadmap));
        when(linkRepository.findMilestoneIdsByRoadmapId(10L)).thenReturn(List.of(100L, 101L));
        RoadmapCommandService service = service(provider, mock(RoadmapGenerationService.class),
                mock(RoadmapPersistenceService.class), roadmapRepository, linkRepository, milestoneRepository);

        service.delete(10L);

        var order = inOrder(linkRepository, roadmapRepository, milestoneRepository);
        order.verify(linkRepository).findMilestoneIdsByRoadmapId(10L);
        order.verify(roadmapRepository).delete(roadmap);
        order.verify(roadmapRepository).flush();
        order.verify(milestoneRepository).deleteUnreferencedByIdsAndUserId(List.of(100L, 101L), 1L);
    }

    @Test
    void rejectsDeletingAnotherUsersOrMissingRoadmap() {
        RoadmapRepository roadmapRepository = mock(RoadmapRepository.class);
        when(roadmapRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        RoadmapCommandService service = service(() -> 1L, mock(RoadmapGenerationService.class),
                mock(RoadmapPersistenceService.class), roadmapRepository,
                mock(RoadmapMilestoneRepository.class), mock(MilestoneRepository.class));

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(RoadmapException.class)
                .extracting(exception -> ((RoadmapException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ROADMAP_NOT_FOUND);
    }

    private RoadmapCommandService service(CurrentUserIdProvider provider,
                                          RoadmapGenerationService generation,
                                          RoadmapPersistenceService persistence,
                                          RoadmapRepository roadmapRepository,
                                          RoadmapMilestoneRepository linkRepository,
                                          MilestoneRepository milestoneRepository) {
        return new RoadmapCommandService(provider, generation, persistence, roadmapRepository,
                linkRepository, milestoneRepository);
    }
}

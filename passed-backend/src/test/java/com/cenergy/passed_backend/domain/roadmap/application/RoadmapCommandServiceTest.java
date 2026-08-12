package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;

import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RoadmapCommandServiceTest {
    @Test
    void usesProviderAndNormalizesJobPostingIdsByDeduplicatingAndSorting() {
        CurrentUserIdProvider provider = () -> 1L;
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(anyLong(), anyList()))
                .thenReturn(new RoadmapGenerationResult("title", List.of()));
        RoadmapPersistenceService persistence = mock(RoadmapPersistenceService.class);
        Roadmap roadmap = mock(Roadmap.class);
        when(roadmap.getId()).thenReturn(77L);
        when(persistence.complete(eq(77L), eq(1L), any())).thenReturn(roadmap);
        RoadmapGenerationClaimService claimService = mock(RoadmapGenerationClaimService.class);
        when(claimService.acquire(1L, "101,102", List.of(101L, 102L)))
                .thenReturn(RoadmapGenerationClaim.acquired(77L));
        RoadmapCommandService service = service(provider, generation, claimService, persistence,
                mock(RoadmapRepository.class), mock(RoadmapMilestoneRepository.class),
                mock(MilestoneRepository.class));

        service.generate(new RoadmapGenerateRequest(List.of(102L, 101L, 102L)));

        verify(generation).generate(1L, List.of(101L, 102L));
        verify(persistence).complete(eq(77L), eq(1L), any());
    }

    @Test
    void rejectsDuplicateActiveRoadmapBeforeAiGenerationOrPersistence() {
        RoadmapGenerationClaimService claimService = mock(RoadmapGenerationClaimService.class);
        when(claimService.acquire(1L, "101,102", List.of(101L, 102L)))
                .thenReturn(RoadmapGenerationClaim.existing(77L, RoadmapStatus.ACTIVE));
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        RoadmapPersistenceService persistence = mock(RoadmapPersistenceService.class);
        RoadmapCommandService service = service(() -> 1L, generation, claimService, persistence,
                mock(RoadmapRepository.class), mock(RoadmapMilestoneRepository.class),
                mock(MilestoneRepository.class));

        assertThatThrownBy(() -> service.generate(
                new RoadmapGenerateRequest(List.of(102L, 101L, 102L))))
                .isInstanceOfSatisfying(RoadmapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROADMAP_ALREADY_EXISTS);
                    assertThat(exception.getRoadmapId()).isEqualTo(77L);
                });

        verifyNoInteractions(generation, persistence);
    }

    @Test
    void rejectsExistingCreatingRoadmapBeforeAiGeneration() {
        RoadmapGenerationClaimService claimService = mock(RoadmapGenerationClaimService.class);
        when(claimService.acquire(1L, "101", List.of(101L)))
                .thenReturn(RoadmapGenerationClaim.existing(88L, RoadmapStatus.CREATING));
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        RoadmapCommandService service = service(() -> 1L, generation, claimService,
                mock(RoadmapPersistenceService.class), mock(RoadmapRepository.class),
                mock(RoadmapMilestoneRepository.class), mock(MilestoneRepository.class));

        assertThatThrownBy(() -> service.generate(new RoadmapGenerateRequest(List.of(101L))))
                .isInstanceOfSatisfying(RoadmapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROADMAP_GENERATION_IN_PROGRESS);
                    assertThat(exception.getRoadmapId()).isEqualTo(88L);
                });
        verifyNoInteractions(generation);
    }

    @Test
    void marksClaimedRoadmapFailedWhenAiGenerationFails() {
        RoadmapGenerationClaimService claimService = mock(RoadmapGenerationClaimService.class);
        when(claimService.acquire(1L, "101", List.of(101L)))
                .thenReturn(RoadmapGenerationClaim.acquired(99L));
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        RoadmapException failure = new RoadmapException(ErrorCode.ROADMAP_GENERATION_FAILED, "failed");
        when(generation.generate(1L, List.of(101L))).thenThrow(failure);
        RoadmapCommandService service = service(() -> 1L, generation, claimService,
                mock(RoadmapPersistenceService.class), mock(RoadmapRepository.class),
                mock(RoadmapMilestoneRepository.class), mock(MilestoneRepository.class));

        assertThatThrownBy(() -> service.generate(new RoadmapGenerateRequest(List.of(101L))))
                .isSameAs(failure);
        verify(claimService).markFailed(99L, "Roadmap generation failed");
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
                mock(RoadmapGenerationClaimService.class), mock(RoadmapPersistenceService.class),
                roadmapRepository, linkRepository, milestoneRepository);

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
                mock(RoadmapGenerationClaimService.class), mock(RoadmapPersistenceService.class), roadmapRepository,
                mock(RoadmapMilestoneRepository.class), mock(MilestoneRepository.class));

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(RoadmapException.class)
                .extracting(exception -> ((RoadmapException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ROADMAP_NOT_FOUND);
    }

    private RoadmapCommandService service(CurrentUserIdProvider provider,
                                          RoadmapGenerationService generation,
                                          RoadmapGenerationClaimService claimService,
                                          RoadmapPersistenceService persistence,
                                          RoadmapRepository roadmapRepository,
                                          RoadmapMilestoneRepository linkRepository,
                                          MilestoneRepository milestoneRepository) {
        return new RoadmapCommandService(provider, generation, claimService, persistence, roadmapRepository,
                linkRepository, milestoneRepository);
    }
}

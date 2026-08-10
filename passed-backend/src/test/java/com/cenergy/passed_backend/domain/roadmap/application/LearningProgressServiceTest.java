package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.dto.MilestoneCompletionRequest;
import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LearningProgressServiceTest {
    @Test
    void completesMilestoneAndSynchronizesParents() {
        MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
        RoadmapProgressSynchronizer synchronizer = mock(RoadmapProgressSynchronizer.class);
        Milestone milestone = Milestone.create(1L, 2L, "AWS", "objective", "criteria", 1, 2,
                MilestoneType.PRACTICE, Difficulty.INTERMEDIATE, 60);
        when(milestoneRepository.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(milestone));
        LearningProgressService service = new LearningProgressService(
                () -> 1L, milestoneRepository, synchronizer);

        var response = service.changeCompletion(10L, new MilestoneCompletionRequest(true));

        assertThat(response.completed()).isTrue();
        assertThat(response.previousProgress()).isZero();
        assertThat(response.currentProgress()).isEqualByComparingTo("100");
        assertThat(response.status()).isEqualTo(MilestoneStatus.COMPLETED);
        verify(synchronizer).synchronizeByMilestone(10L);
    }
}

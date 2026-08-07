package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.MilestoneCompletionRequest;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.LearningProgressRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LearningProgressServiceTest {
    @Test
    void completesMilestoneRecordsHistoryAndSynchronizesParents() {
        MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
        LearningProgressRepository progressRepository = mock(LearningProgressRepository.class);
        RoadmapProgressSynchronizer synchronizer = mock(RoadmapProgressSynchronizer.class);
        Milestone milestone = Milestone.create(1L, 2L, "AWS", "목표", "완료", 1, 2,
                MilestoneType.PRACTICE, Difficulty.INTERMEDIATE, 60);
        when(milestoneRepository.findOwnedForUpdate(10L, 1L)).thenReturn(Optional.of(milestone));
        LearningProgressService service = new LearningProgressService(
                () -> 1L, milestoneRepository, progressRepository, synchronizer);

        var response = service.changeCompletion(
                10L, new MilestoneCompletionRequest(true, 30, "실습 완료"));

        assertThat(response.completed()).isTrue();
        assertThat(response.previousProgress()).isZero();
        assertThat(response.currentProgress()).isEqualByComparingTo("100");
        assertThat(response.status()).isEqualTo(MilestoneStatus.COMPLETED);
        ArgumentCaptor<LearningProgress> history = ArgumentCaptor.forClass(LearningProgress.class);
        verify(progressRepository).save(history.capture());
        assertThat(history.getValue().getPreviousProgress()).isZero();
        assertThat(history.getValue().getCurrentProgress()).isEqualByComparingTo("100");
        assertThat(history.getValue().getStudiedMinutes()).isEqualTo(30);
        assertThat(history.getValue().getNote()).isEqualTo("실습 완료");
        verify(synchronizer).synchronizeByMilestone(10L);
    }
}

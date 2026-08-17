package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapSkillRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class RoadmapProgressSynchronizerTest {
    @Test
    void initializesProgressFromReusedCompletedMilestonesWithoutChangingEstimatedMinutes() {
        RoadmapMilestoneRepository linkRepository = mock(RoadmapMilestoneRepository.class);
        RoadmapSkillRepository skillRepository = mock(RoadmapSkillRepository.class);
        RoadmapRepository roadmapRepository = mock(RoadmapRepository.class);
        RoadmapEtaCalculator etaCalculator = mock(RoadmapEtaCalculator.class);
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapSkill skill = mock(RoadmapSkill.class);
        RoadmapMilestone completed = link(true, MilestoneStatus.COMPLETED);
        RoadmapMilestone incomplete = link(true, MilestoneStatus.NOT_STARTED);

        when(skill.getId()).thenReturn(10L);
        when(roadmap.getDailyStudyMinutes()).thenReturn(60);
        when(roadmapRepository.findById(100L)).thenReturn(Optional.of(roadmap));
        when(skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(100L)).thenReturn(List.of(skill));
        when(linkRepository.findAllByRoadmapSkillIds(List.of(10L)))
                .thenReturn(List.of(completed, incomplete));
        when(etaCalculator.calculate(List.of(completed, incomplete), 60))
                .thenReturn(java.time.LocalDate.of(2026, 8, 8));

        new RoadmapProgressSynchronizer(linkRepository, skillRepository, roadmapRepository, etaCalculator)
                .synchronizeInitialProgress(100L);

        verify(skill).updateProgressRate(new BigDecimal("50.00"));
        verify(skill, never()).updateEstimatedMinutes(anyInt());
        verify(roadmap).updateProgressRate(new BigDecimal("50.00"));
        verify(roadmap, never()).updateTotalEstimatedMinutes(anyInt());
        verify(roadmap).updateEstimatedEndDate(java.time.LocalDate.of(2026, 8, 8));
    }

    @Test
    void recalculatesEveryAffectedSkillAndRoadmapByAllMilestoneCount() {
        RoadmapMilestoneRepository linkRepository = mock(RoadmapMilestoneRepository.class);
        RoadmapSkillRepository skillRepository = mock(RoadmapSkillRepository.class);
        RoadmapRepository roadmapRepository = mock(RoadmapRepository.class);
        RoadmapEtaCalculator etaCalculator = mock(RoadmapEtaCalculator.class);
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapSkill skill = mock(RoadmapSkill.class);
        RoadmapMilestone completed = link(true, MilestoneStatus.COMPLETED);
        RoadmapMilestone incomplete = link(false, MilestoneStatus.NOT_STARTED);

        when(roadmap.getId()).thenReturn(100L);
        when(roadmap.getDailyStudyMinutes()).thenReturn(60);
        when(skill.getId()).thenReturn(10L);
        when(skill.getRoadmap()).thenReturn(roadmap);
        when(linkRepository.findRoadmapSkillIdsByMilestoneId(1L)).thenReturn(List.of(10L));
        when(skillRepository.findAllById(List.of(10L))).thenReturn(List.of(skill));
        when(linkRepository.findAllByRoadmapSkillIds(List.of(10L)))
                .thenReturn(List.of(completed, incomplete));
        when(roadmapRepository.findById(100L)).thenReturn(Optional.of(roadmap));
        when(skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(100L)).thenReturn(List.of(skill));
        when(etaCalculator.calculate(List.of(completed, incomplete), 60))
                .thenReturn(java.time.LocalDate.of(2026, 8, 8));

        new RoadmapProgressSynchronizer(linkRepository, skillRepository, roadmapRepository, etaCalculator)
                .synchronizeByMilestone(1L);

        verify(skill).updateProgressRate(new BigDecimal("50.00"));
        verify(roadmap).updateProgressRate(new BigDecimal("50.00"));
        verify(roadmap).updateEstimatedEndDate(java.time.LocalDate.of(2026, 8, 8));
    }

    private RoadmapMilestone link(boolean required, MilestoneStatus status) {
        RoadmapMilestone link = mock(RoadmapMilestone.class);
        Milestone milestone = mock(Milestone.class);
        RoadmapSkill skill = mock(RoadmapSkill.class);
        when(link.isRequired()).thenReturn(required);
        when(link.getMilestone()).thenReturn(milestone);
        when(link.getRoadmapSkill()).thenReturn(skill);
        when(skill.getId()).thenReturn(10L);
        when(milestone.getStatus()).thenReturn(status);
        return link;
    }
}

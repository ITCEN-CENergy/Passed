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
    void recalculatesEveryAffectedSkillAndRoadmapByRequiredMilestoneCount() {
        RoadmapMilestoneRepository linkRepository = mock(RoadmapMilestoneRepository.class);
        RoadmapSkillRepository skillRepository = mock(RoadmapSkillRepository.class);
        RoadmapRepository roadmapRepository = mock(RoadmapRepository.class);
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapSkill skill = mock(RoadmapSkill.class);
        RoadmapMilestone completed = link(true, MilestoneStatus.COMPLETED);
        RoadmapMilestone incomplete = link(true, MilestoneStatus.NOT_STARTED);

        when(roadmap.getId()).thenReturn(100L);
        when(skill.getId()).thenReturn(10L);
        when(skill.getRoadmap()).thenReturn(roadmap);
        when(linkRepository.findRoadmapSkillIdsByMilestoneId(1L)).thenReturn(List.of(10L));
        when(skillRepository.findAllById(List.of(10L))).thenReturn(List.of(skill));
        when(linkRepository.findAllByRoadmapSkillIds(List.of(10L)))
                .thenReturn(List.of(completed, incomplete));
        when(roadmapRepository.findById(100L)).thenReturn(Optional.of(roadmap));
        when(skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(100L)).thenReturn(List.of(skill));

        new RoadmapProgressSynchronizer(linkRepository, skillRepository, roadmapRepository)
                .synchronizeByMilestone(1L);

        verify(skill).updateProgressRate(new BigDecimal("50.00"));
        verify(roadmap).updateProgressRate(new BigDecimal("50.00"));
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

package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MilestoneReuseServiceTest {
    private final MilestoneReuseService service = new MilestoneReuseService(mock(MilestoneRepository.class));

    @Test
    void reusesSameCompetencyAndTypeWhenExistingRangeCoversCandidate() {
        Milestone existing = milestone(1L, 1, 3, MilestoneType.PRACTICE);

        MilestoneReuseDecision decision = service.decide(
                skill(1L, 1, 3), candidate(1, 2, MilestoneType.PRACTICE), List.of(existing));

        assertThat(decision.reuseType()).isEqualTo(ReuseType.REUSED);
        assertThat(decision.milestone()).isSameAs(existing);
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    void doesNotReuseDifferentTypeOrInsufficientRange() {
        List<Milestone> existing = List.of(
                milestone(1L, 1, 3, MilestoneType.CONCEPT),
                milestone(1L, 1, 1, MilestoneType.PRACTICE));

        MilestoneReuseDecision decision = service.decide(
                skill(1L, 1, 3), candidate(1, 2, MilestoneType.PRACTICE), existing);

        assertThat(decision.reuseType()).isEqualTo(ReuseType.NEW);
        assertThat(decision.milestone()).isNull();
    }

    @Test
    void reusesMatchingMilestoneRegardlessOfCompetencyGap() {
        Milestone existing = milestone(1L, 1, 3, MilestoneType.PRACTICE);

        MilestoneReuseDecision decision = service.decide(
                skill(1L, 3, 3), candidate(3, 3, MilestoneType.PRACTICE), List.of(existing));

        assertThat(decision.reuseType()).isEqualTo(ReuseType.REUSED);
        assertThat(decision.milestone()).isSameAs(existing);
    }

    @Test
    void doesNotAssignTheSameExistingMilestoneTwiceWithinOneRoadmapSkill() {
        Milestone alreadyAssigned = reusableMilestone(10L, 1L, 0, 1, MilestoneType.CERTIFICATION);
        Milestone available = reusableMilestone(11L, 1L, 0, 1, MilestoneType.CERTIFICATION);

        MilestoneReuseDecision decision = service.decide(
                skill(1L, 0, 1),
                candidate(0, 1, MilestoneType.CERTIFICATION),
                List.of(alreadyAssigned, available),
                Set.of(alreadyAssigned.getId()));

        assertThat(decision.reuseType()).isEqualTo(ReuseType.REUSED);
        assertThat(decision.milestone()).isSameAs(available);
    }

    private RoadmapGenerationResult.Skill skill(Long competencyId, int currentLevel, int targetLevel) {
        return new RoadmapGenerationResult.Skill("key", competencyId, "Java",
                CompetencyCategory.TECHNICAL_SKILL, currentLevel, targetLevel,
                RequirementType.REQUIRED, Math.max(targetLevel - currentLevel, 0), 1,
                100, 1, List.of(), List.of());
    }

    private RoadmapGenerationResult.Milestone candidate(int startLevel, int targetLevel, MilestoneType type) {
        return new RoadmapGenerationResult.Milestone("title", null, "objective", "criteria",
                startLevel, targetLevel, type, Difficulty.INTERMEDIATE, 60, 1, List.of());
    }

    private Milestone milestone(Long competencyId, int startLevel, int targetLevel, MilestoneType type) {
        return Milestone.create(1L, competencyId, "existing", "objective", "criteria",
                startLevel, targetLevel, type, Difficulty.INTERMEDIATE, 60);
    }

    private Milestone reusableMilestone(Long id, Long competencyId, int startLevel, int targetLevel,
                                        MilestoneType type) {
        Milestone milestone = mock(Milestone.class);
        when(milestone.getId()).thenReturn(id);
        when(milestone.getStandardCompetencyId()).thenReturn(competencyId);
        when(milestone.getStartLevel()).thenReturn(startLevel);
        when(milestone.getTargetLevel()).thenReturn(targetLevel);
        when(milestone.getMilestoneType()).thenReturn(type);
        return milestone;
    }
}

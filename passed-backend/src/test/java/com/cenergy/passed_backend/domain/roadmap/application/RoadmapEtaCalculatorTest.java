package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapMilestone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoadmapEtaCalculatorTest {
    private final RoadmapEtaCalculator calculator = new RoadmapEtaCalculator(60);
    private final LocalDate baseDate = LocalDate.of(2026, 8, 7);

    @Test
    void roundsRemainingStudyTimeUpToWholeDays() {
        assertThat(calculator.calculate(List.of(link(true, MilestoneStatus.NOT_STARTED, 61)), baseDate))
                .isEqualTo(LocalDate.of(2026, 8, 9));
    }

    @Test
    void excludesCompletedButIncludesOptionalMilestones() {
        List<RoadmapMilestone> milestones = List.of(
                link(true, MilestoneStatus.COMPLETED, 300),
                link(false, MilestoneStatus.NOT_STARTED, 300),
                link(true, MilestoneStatus.NOT_STARTED, 60));

        assertThat(calculator.calculate(milestones, baseDate))
                .isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void returnsBaseDateWhenEveryRequiredMilestoneIsCompleted() {
        assertThat(calculator.calculate(
                List.of(link(true, MilestoneStatus.COMPLETED, 60)), baseDate))
                .isEqualTo(baseDate);
    }

    @Test
    void usesRoadmapSpecificDailyStudyMinutes() {
        List<RoadmapMilestone> milestones = List.of(
                link(true, MilestoneStatus.NOT_STARTED, 240));

        assertThat(calculator.calculate(milestones, baseDate, 120))
                .isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(calculator.calculate(milestones, baseDate, 30))
                .isEqualTo(LocalDate.of(2026, 8, 15));
    }

    private RoadmapMilestone link(boolean required, MilestoneStatus status, int estimatedMinutes) {
        RoadmapMilestone link = mock(RoadmapMilestone.class);
        Milestone milestone = mock(Milestone.class);
        when(link.isRequired()).thenReturn(required);
        when(link.getMilestone()).thenReturn(milestone);
        when(milestone.getStatus()).thenReturn(status);
        when(milestone.getEstimatedMinutes()).thenReturn(estimatedMinutes);
        return link;
    }
}

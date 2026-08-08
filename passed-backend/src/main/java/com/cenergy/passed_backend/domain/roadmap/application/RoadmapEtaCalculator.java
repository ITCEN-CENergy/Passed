package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapMilestone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;

@Component
public class RoadmapEtaCalculator {
    private final int dailyStudyMinutes;

    public RoadmapEtaCalculator(
            @Value("${roadmap.eta.daily-study-minutes:60}") int dailyStudyMinutes
    ) {
        if (dailyStudyMinutes <= 0) {
            throw new IllegalArgumentException("dailyStudyMinutes must be positive");
        }
        this.dailyStudyMinutes = dailyStudyMinutes;
    }

    public LocalDate calculate(Collection<RoadmapMilestone> milestones) {
        return calculate(milestones, LocalDate.now());
    }

    LocalDate calculate(Collection<RoadmapMilestone> milestones, LocalDate baseDate) {
        long remainingMinutes = milestones.stream()
                .filter(RoadmapMilestone::isRequired)
                .filter(link -> link.getMilestone().getStatus() != MilestoneStatus.COMPLETED)
                .mapToLong(link -> link.getMilestone().getEstimatedMinutes())
                .sum();
        long remainingDays = (remainingMinutes + dailyStudyMinutes - 1) / dailyStudyMinutes;
        return baseDate.plusDays(remainingDays);
    }
}

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
        return calculate(milestones, dailyStudyMinutes);
    }

    public LocalDate calculate(Collection<RoadmapMilestone> milestones, int studyMinutesPerDay) {
        return calculate(milestones, LocalDate.now(), studyMinutesPerDay);
    }

    public LocalDate calculateRemainingMinutes(int remainingMinutes) {
        return calculateRemainingMinutes(remainingMinutes, dailyStudyMinutes);
    }

    public LocalDate calculateRemainingMinutes(int remainingMinutes, int studyMinutesPerDay) {
        if (remainingMinutes < 0) throw new IllegalArgumentException("remainingMinutes must not be negative");
        requirePositive(studyMinutesPerDay);
        long remainingDays = (remainingMinutes + (long) studyMinutesPerDay - 1) / studyMinutesPerDay;
        return LocalDate.now().plusDays(remainingDays);
    }

    public int dailyStudyMinutes() {
        return dailyStudyMinutes;
    }

    LocalDate calculate(Collection<RoadmapMilestone> milestones, LocalDate baseDate) {
        return calculate(milestones, baseDate, dailyStudyMinutes);
    }

    LocalDate calculate(Collection<RoadmapMilestone> milestones, LocalDate baseDate,
                        int studyMinutesPerDay) {
        requirePositive(studyMinutesPerDay);
        long remainingMinutes = milestones.stream()
                .filter(RoadmapMilestone::isRequired)
                .filter(link -> link.getMilestone().getStatus() != MilestoneStatus.COMPLETED)
                .mapToLong(link -> link.getMilestone().getEstimatedMinutes())
                .sum();
        long remainingDays = (remainingMinutes + studyMinutesPerDay - 1) / studyMinutesPerDay;
        return baseDate.plusDays(remainingDays);
    }

    private void requirePositive(int value) {
        if (value <= 0) throw new IllegalArgumentException("studyMinutesPerDay must be positive");
    }
}

package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapScheduleStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record RoadmapScheduleAssessment(
        RoadmapScheduleStatus status,
        long delayDays,
        boolean replanRecommended
) {
    static final long REPLAN_RECOMMENDATION_DELAY_DAYS = 3;

    public static RoadmapScheduleAssessment assess(LocalDate baselineEndDate,
                                                    LocalDate currentEstimatedEndDate) {
        if (baselineEndDate == null || currentEstimatedEndDate == null) {
            return new RoadmapScheduleAssessment(RoadmapScheduleStatus.UNKNOWN, 0, false);
        }
        long delayDays = Math.max(ChronoUnit.DAYS.between(
                baselineEndDate, currentEstimatedEndDate), 0);
        RoadmapScheduleStatus status = delayDays > 0
                ? RoadmapScheduleStatus.DELAYED
                : RoadmapScheduleStatus.ON_TRACK;
        return new RoadmapScheduleAssessment(status, delayDays,
                delayDays >= REPLAN_RECOMMENDATION_DELAY_DAYS);
    }
}

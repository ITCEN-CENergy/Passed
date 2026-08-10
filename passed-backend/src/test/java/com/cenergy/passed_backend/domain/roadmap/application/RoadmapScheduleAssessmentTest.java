package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapScheduleStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapScheduleAssessmentTest {
    private final LocalDate baseline = LocalDate.of(2026, 8, 20);

    @Test
    void reportsUnknownWhenEitherEndDateIsMissing() {
        assertThat(RoadmapScheduleAssessment.assess(null, baseline))
                .isEqualTo(new RoadmapScheduleAssessment(RoadmapScheduleStatus.UNKNOWN, 0, false));
        assertThat(RoadmapScheduleAssessment.assess(baseline, null))
                .isEqualTo(new RoadmapScheduleAssessment(RoadmapScheduleStatus.UNKNOWN, 0, false));
    }

    @Test
    void reportsOnTrackWithoutNegativeDelayWhenCurrentEtaIsNotLater() {
        assertThat(RoadmapScheduleAssessment.assess(baseline, baseline.minusDays(2)))
                .isEqualTo(new RoadmapScheduleAssessment(RoadmapScheduleStatus.ON_TRACK, 0, false));
    }

    @Test
    void reportsDelayWithoutRecommendationBeforeThreshold() {
        assertThat(RoadmapScheduleAssessment.assess(baseline, baseline.plusDays(2)))
                .isEqualTo(new RoadmapScheduleAssessment(RoadmapScheduleStatus.DELAYED, 2, false));
    }

    @Test
    void recommendsReplanningFromThreeDaysOfDelay() {
        assertThat(RoadmapScheduleAssessment.assess(baseline, baseline.plusDays(3)))
                .isEqualTo(new RoadmapScheduleAssessment(RoadmapScheduleStatus.DELAYED, 3, true));
    }
}

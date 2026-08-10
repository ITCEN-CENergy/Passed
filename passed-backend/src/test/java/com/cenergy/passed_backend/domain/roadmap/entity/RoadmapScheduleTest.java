package com.cenergy.passed_backend.domain.roadmap.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapScheduleTest {
    @Test
    void initializesBaselineAndCurrentEtaTogether() {
        Roadmap roadmap = Roadmap.create(1L);
        LocalDate endDate = LocalDate.of(2026, 8, 20);

        roadmap.initializeEndDate(endDate);

        assertThat(roadmap.getBaselineEndDate()).isEqualTo(endDate);
        assertThat(roadmap.getEstimatedEndDate()).isEqualTo(endDate);
    }

    @Test
    void updatingEtaDoesNotChangeBaseline() {
        Roadmap roadmap = Roadmap.create(1L);
        LocalDate baseline = LocalDate.of(2026, 8, 20);
        roadmap.initializeEndDate(baseline);

        roadmap.updateEstimatedEndDate(baseline.plusDays(4));

        assertThat(roadmap.getBaselineEndDate()).isEqualTo(baseline);
        assertThat(roadmap.getEstimatedEndDate()).isEqualTo(baseline.plusDays(4));
    }

    @Test
    void doesNotAllowBaselineToBeInitializedTwice() {
        Roadmap roadmap = Roadmap.create(1L);
        roadmap.initializeEndDate(LocalDate.of(2026, 8, 20));

        assertThatThrownBy(() -> roadmap.initializeEndDate(LocalDate.of(2026, 8, 21)))
                .isInstanceOf(IllegalStateException.class);
    }
}

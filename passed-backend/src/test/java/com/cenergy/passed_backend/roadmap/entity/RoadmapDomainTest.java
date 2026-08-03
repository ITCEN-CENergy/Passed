package com.cenergy.passed_backend.roadmap.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapDomainTest {
    @Test
    void roadmapHasCreatingDefaults() {
        Roadmap roadmap = Roadmap.create(1L);
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.CREATING);
        assertThat(roadmap.getTotalEstimatedMinutes()).isZero();
        assertThat(roadmap.getProgressRate()).isZero();
    }

    @Test
    void roadmapCanBecomeActive() {
        Roadmap roadmap = Roadmap.create(1L);
        roadmap.activate("백엔드 로드맵", 120);
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.ACTIVE);
        assertThat(roadmap.getTitle()).isEqualTo("백엔드 로드맵");
        assertThat(roadmap.getTotalEstimatedMinutes()).isEqualTo(120);
    }

    @Test
    void roadmapCanFailWithSafeReason() {
        Roadmap roadmap = Roadmap.create(1L);
        roadmap.fail("로드맵 생성에 실패했습니다.");
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.FAILED);
        assertThat(roadmap.getFailureReason()).isEqualTo("로드맵 생성에 실패했습니다.");
    }

    @Test
    void milestoneHasDefaults() {
        Milestone milestone = Milestone.create(1L, 2L, "Java", "학습", "통과", 0, 1,
                MilestoneType.CONCEPT, Difficulty.BEGINNER, 30);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.NOT_STARTED);
        assertThat(milestone.getProgressRate()).isZero();
    }

    @Test
    void roadmapMilestoneHasDefaults() {
        RoadmapMilestone roadmapMilestone = RoadmapMilestone.create();
        assertThat(roadmapMilestone.getReuseType()).isEqualTo(ReuseType.NEW);
        assertThat(roadmapMilestone.isRequired()).isTrue();
    }
}

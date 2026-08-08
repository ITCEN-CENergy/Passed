package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "roadmap_milestones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadmapMilestone extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_skill_id", nullable = false)
    private RoadmapSkill roadmapSkill;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;
    @Column(name = "learning_order", nullable = false)
    private Integer learningOrder;
    @Enumerated(EnumType.STRING)
    @Column(name = "reuse_type", nullable = false, length = 50)
    private ReuseType reuseType = ReuseType.NEW;
    @Column(name = "reuse_reason", columnDefinition = "text")
    private String reuseReason;
    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    public static RoadmapMilestone create(RoadmapSkill roadmapSkill, Milestone milestone, int learningOrder) {
        return create(roadmapSkill, milestone, learningOrder, ReuseType.NEW, null);
    }

    public static RoadmapMilestone create(RoadmapSkill roadmapSkill, Milestone milestone, int learningOrder,
                                          ReuseType reuseType, String reuseReason) {
        RoadmapMilestone value = new RoadmapMilestone();
        value.roadmapSkill = roadmapSkill;
        value.milestone = milestone;
        value.learningOrder = learningOrder;
        value.reuseType = reuseType;
        value.reuseReason = reuseReason;
        return value;
    }

    public static RoadmapMilestone create() {
        return new RoadmapMilestone();
    }
}

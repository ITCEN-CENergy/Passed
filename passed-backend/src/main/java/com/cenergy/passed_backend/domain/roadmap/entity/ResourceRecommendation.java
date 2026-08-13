package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "resource_recommendations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceRecommendation extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private LearningResource resource;

    @Column(name = "rank_order")
    private Integer rankOrder;

    @Column(name = "recommendation_reason", columnDefinition = "text")
    private String recommendationReason;

    public static ResourceRecommendation create(Milestone milestone, LearningResource resource, int rankOrder) {
        ResourceRecommendation value = new ResourceRecommendation();
        value.milestone = milestone;
        value.resource = resource;
        value.rankOrder = rankOrder;
        return value;
    }
}

package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "roadmap_skills")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadmapSkill extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;
    @Column(name = "standard_competency_id", nullable = false)
    private Long standardCompetencyId;
    @Column(name = "standard_competency_name", nullable = false, length = 200)
    private String standardCompetencyName;
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private CompetencyCategory category;
    @Column(name = "current_level", nullable = false)
    private Integer currentLevel;
    @Column(name = "target_level", nullable = false)
    private Integer targetLevel;
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 50)
    private RequirementType requirementType;
    @Column(name = "gap_level", nullable = false)
    private Integer gapLevel;
    @Column(name = "frequency", nullable = false)
    private Integer frequency;
    @Column(name = "priority_score", nullable = false, precision = 10, scale = 4)
    private BigDecimal priorityScore;
    @Column(name = "priority", nullable = false)
    private Integer priority;
    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes = 0;
    @Column(name = "progress_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressRate = BigDecimal.ZERO;

    public static RoadmapSkill create(Roadmap roadmap, Long competencyId, String competencyName,
                                      CompetencyCategory category, int currentLevel, int targetLevel,
                                      RequirementType requirementType, int gapLevel, int frequency,
                                      int priorityScore, int priority, int estimatedMinutes) {
        RoadmapSkill value = new RoadmapSkill();
        value.roadmap = roadmap;
        value.standardCompetencyId = competencyId;
        value.standardCompetencyName = competencyName;
        value.category = category;
        value.currentLevel = currentLevel;
        value.targetLevel = targetLevel;
        value.requirementType = requirementType;
        value.gapLevel = gapLevel;
        value.frequency = frequency;
        value.priorityScore = BigDecimal.valueOf(priorityScore);
        value.priority = priority;
        value.estimatedMinutes = estimatedMinutes;
        return value;
    }
}

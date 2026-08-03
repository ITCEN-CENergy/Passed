package com.cenergy.passed_backend.roadmap.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter @Entity @Table(name = "milestones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Milestone extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "standard_competency_id", nullable = false) private Long standardCompetencyId;
    @Column(name = "title", nullable = false, length = 200) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "learning_objective", nullable = false, columnDefinition = "text") private String learningObjective;
    @Column(name = "completion_criteria", nullable = false, columnDefinition = "text") private String completionCriteria;
    @Column(name = "start_level", nullable = false) private Integer startLevel;
    @Column(name = "target_level", nullable = false) private Integer targetLevel;
    @Enumerated(EnumType.STRING) @Column(name = "milestone_type", nullable = false, length = 50) private MilestoneType milestoneType;
    @Enumerated(EnumType.STRING) @Column(name = "difficulty", nullable = false, length = 50) private Difficulty difficulty;
    @Column(name = "estimated_minutes", nullable = false) private Integer estimatedMinutes;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 50) private MilestoneStatus status = MilestoneStatus.NOT_STARTED;
    @Column(name = "progress_rate", nullable = false, precision = 5, scale = 2) private BigDecimal progressRate = BigDecimal.ZERO;
    @Column(name = "completed_at") private OffsetDateTime completedAt;

    public static Milestone create(Long userId, Long competencyId, String title, String objective,
                                   String criteria, int startLevel, int targetLevel, MilestoneType type,
                                   Difficulty difficulty, int estimatedMinutes) {
        Milestone value = new Milestone();
        value.userId = userId; value.standardCompetencyId = competencyId; value.title = title;
        value.learningObjective = objective; value.completionCriteria = criteria;
        value.startLevel = startLevel; value.targetLevel = targetLevel; value.milestoneType = type;
        value.difficulty = difficulty; value.estimatedMinutes = estimatedMinutes;
        return value;
    }
}

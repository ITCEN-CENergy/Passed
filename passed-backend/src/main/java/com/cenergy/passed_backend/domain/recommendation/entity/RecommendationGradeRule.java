package com.cenergy.passed_backend.domain.recommendation.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "recommendation_grade_rules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rec_grade_rule_policy_grade",
                        columnNames = {"scoring_policy_id", "recommendation_grade"}
                ),
                @UniqueConstraint(
                        name = "uk_rec_grade_rule_policy_priority",
                        columnNames = {"scoring_policy_id", "priority"}
                )
        },
        indexes = @Index(
                name = "idx_rec_grade_rules_scoring_policy_id",
                columnList = "scoring_policy_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationGradeRule extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scoring_policy_id", nullable = false)
    private RecommendationScoringPolicy scoringPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_grade", length = 30, nullable = false)
    private RecommendationGrade recommendationGrade;

    @Column(name = "display_name", length = 50, nullable = false)
    private String displayName;

    @Column(name = "min_total_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal minTotalScore;

    @Column(name = "min_required_coverage_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal minRequiredCoverageRate = BigDecimal.ZERO;

    @Column(name = "min_required_level_match_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal minRequiredLevelMatchRate = BigDecimal.ZERO;

    @Column(name = "min_important_match_count", nullable = false)
    private int minImportantMatchCount;

    @Column(name = "priority", nullable = false)
    private int priority;
}

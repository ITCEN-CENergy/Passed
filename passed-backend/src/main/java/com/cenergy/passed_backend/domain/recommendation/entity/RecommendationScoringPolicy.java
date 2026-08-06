package com.cenergy.passed_backend.domain.recommendation.entity;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "recommendation_scoring_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rec_scoring_policy_code_version",
                columnNames = {"policy_code", "version"}
        ),
        indexes = @Index(
                name = "idx_rec_scoring_policies_status",
                columnList = "status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationScoringPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", length = 50, nullable = false)
    private String policyCode;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "policy_name", length = 100, nullable = false)
    private String policyName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "required_max_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal requiredMaxScore;

    @Column(name = "preferred_max_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal preferredMaxScore;

    @Column(name = "related_max_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal relatedMaxScore;

    @Column(name = "important_bonus_max_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal importantBonusMaxScore;

    @Column(name = "required_coverage_threshold", precision = 5, scale = 4, nullable = false)
    private BigDecimal requiredCoverageThreshold;

    @Column(name = "primary_important_match_count", nullable = false)
    private int primaryImportantMatchCount = 1;

    @Column(name = "important_required_weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal importantRequiredWeight;

    @Column(name = "important_preferred_weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal importantPreferredWeight;

    @Column(name = "important_related_weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal importantRelatedWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RecommendationPolicyStatus status = RecommendationPolicyStatus.DRAFT;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "retired_at")
    private OffsetDateTime retiredAt;
}

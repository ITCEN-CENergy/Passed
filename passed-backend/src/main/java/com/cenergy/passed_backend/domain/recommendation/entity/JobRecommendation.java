package com.cenergy.passed_backend.domain.recommendation.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "job_recommendations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_rec_run_job_posting",
                        columnNames = {"recommendation_run_id", "job_posting_id"}
                ),
                @UniqueConstraint(
                        name = "uk_job_rec_run_rank_order",
                        columnNames = {"recommendation_run_id", "rank_order"}
                )
        },
        indexes = {
                @Index(name = "idx_job_recommendations_run_id", columnList = "recommendation_run_id"),
                @Index(name = "idx_job_recommendations_job_posting_id", columnList = "job_posting_id"),
                @Index(name = "idx_job_recommendations_grade", columnList = "recommendation_grade"),
                @Index(name = "idx_job_recommendations_candidate_tier", columnList = "candidate_tier")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_run_id", nullable = false)
    private RecommendationRun recommendationRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "total_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal totalScore;

    @Column(name = "required_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal requiredScore;

    @Column(name = "preferred_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal preferredScore;

    @Column(name = "related_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal relatedScore;

    @Column(name = "important_skill_bonus", precision = 7, scale = 4, nullable = false)
    private BigDecimal importantSkillBonus;

    @Column(name = "required_skill_count", nullable = false)
    private int requiredSkillCount;

    @Column(name = "required_owned_count", nullable = false)
    private int requiredOwnedCount;

    @Column(name = "required_coverage_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal requiredCoverageRate;

    @Column(name = "required_level_match_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal requiredLevelMatchRate;

    @Column(name = "important_skill_count", nullable = false)
    private int importantSkillCount;

    @Column(name = "important_match_count", nullable = false)
    private int importantMatchCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_tier", length = 20, nullable = false)
    private RecommendationCandidateTier candidateTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_grade", length = 30, nullable = false)
    private RecommendationGrade recommendationGrade;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "text")
    private String weaknesses;

    public static JobRecommendation create(
            RecommendationRun recommendationRun,
            JobPosting jobPosting,
            BigDecimal totalScore,
            BigDecimal requiredScore,
            BigDecimal preferredScore,
            BigDecimal relatedScore,
            BigDecimal importantSkillBonus,
            int requiredSkillCount,
            int requiredOwnedCount,
            BigDecimal requiredCoverageRate,
            BigDecimal requiredLevelMatchRate,
            int importantSkillCount,
            int importantMatchCount,
            RecommendationCandidateTier candidateTier,
            RecommendationGrade recommendationGrade,
            int rankOrder,
            String reason,
            String strengths,
            String weaknesses
    ) {
        JobRecommendation value = new JobRecommendation();
        value.recommendationRun = Objects.requireNonNull(
                recommendationRun,
                "recommendationRun must not be null"
        );
        value.jobPosting = Objects.requireNonNull(jobPosting, "jobPosting must not be null");
        value.totalScore = Objects.requireNonNull(totalScore, "totalScore must not be null");
        value.requiredScore = Objects.requireNonNull(requiredScore, "requiredScore must not be null");
        value.preferredScore = Objects.requireNonNull(preferredScore, "preferredScore must not be null");
        value.relatedScore = Objects.requireNonNull(relatedScore, "relatedScore must not be null");
        value.importantSkillBonus = Objects.requireNonNull(
                importantSkillBonus,
                "importantSkillBonus must not be null"
        );
        value.requiredSkillCount = requiredSkillCount;
        value.requiredOwnedCount = requiredOwnedCount;
        value.requiredCoverageRate = Objects.requireNonNull(
                requiredCoverageRate,
                "requiredCoverageRate must not be null"
        );
        value.requiredLevelMatchRate = Objects.requireNonNull(
                requiredLevelMatchRate,
                "requiredLevelMatchRate must not be null"
        );
        value.importantSkillCount = importantSkillCount;
        value.importantMatchCount = importantMatchCount;
        value.candidateTier = Objects.requireNonNull(candidateTier, "candidateTier must not be null");
        value.recommendationGrade = Objects.requireNonNull(
                recommendationGrade,
                "recommendationGrade must not be null"
        );
        if (rankOrder <= 0) {
            throw new IllegalArgumentException("rankOrder must be positive");
        }
        value.rankOrder = rankOrder;
        value.reason = requireText(reason, "reason");
        value.strengths = strengths;
        value.weaknesses = weaknesses;
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

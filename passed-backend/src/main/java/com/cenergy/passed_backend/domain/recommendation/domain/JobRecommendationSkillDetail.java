package com.cenergy.passed_backend.recommendation.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.skill.entity.Skill;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "job_recommendation_skill_details",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_rec_skill_detail_rec_skill",
                columnNames = {"job_recommendation_id", "skill_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_job_rec_skill_details_job_rec_id",
                        columnList = "job_recommendation_id"
                ),
                @Index(name = "idx_job_rec_skill_details_skill_id", columnList = "skill_id"),
                @Index(
                        name = "idx_job_rec_skill_details_rec_skill_type",
                        columnList = "job_recommendation_id, skill_type"
                ),
                @Index(
                        name = "idx_job_rec_skill_details_rec_important",
                        columnList = "job_recommendation_id, is_user_important"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRecommendationSkillDetail extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_recommendation_id", nullable = false)
    private JobRecommendation jobRecommendation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", length = 20, nullable = false)
    private JobPostingSkillType skillType;

    @Column(name = "required_level", nullable = false)
    private short requiredLevel;

    @Column(name = "user_level")
    private Short userLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", length = 20, nullable = false)
    private SkillEvaluationType evaluationType;

    @Column(name = "is_owned", nullable = false)
    private boolean owned;

    @Column(name = "is_requirement_satisfied", nullable = false)
    private boolean requirementSatisfied;

    @Column(name = "is_user_important", nullable = false)
    private boolean userImportant;

    @Column(name = "match_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal matchRate;

    @Column(name = "base_max_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal baseMaxScore;

    @Column(name = "base_contribution_score", precision = 7, scale = 4, nullable = false)
    private BigDecimal baseContributionScore;

    @Column(
            name = "important_bonus_contribution_score",
            precision = 7,
            scale = 4,
            nullable = false
    )
    private BigDecimal importantBonusContributionScore = BigDecimal.ZERO;
}

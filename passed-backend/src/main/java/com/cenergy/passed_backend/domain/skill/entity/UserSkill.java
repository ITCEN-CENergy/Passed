package com.cenergy.passed_backend.domain.skill.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "user_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_skill",
                columnNames = {"user_id", "skill_id"}
        ),
        indexes = {
                @Index(name = "idx_user_skill_user_id", columnList = "user_id"),
                @Index(name = "idx_user_skill_skill_id", columnList = "skill_id"),
                @Index(name = "idx_user_skill_important", columnList = "is_important")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkill extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "skill_level", nullable = false)
    private short skillLevel = 1;

    @Column(name = "is_important", nullable = false)
    private boolean important;

    @Column(name = "mapping_confidence", precision = 4, scale = 3)
    private BigDecimal mappingConfidence;

    @Column(name = "level_confidence", precision = 4, scale = 3)
    private BigDecimal levelConfidence;

    /**
     * 사용자가 최종 확인한 숙련도와 추천 강조 여부를 반영한다.
     * AI가 계산한 숙련도를 사용자가 바꾼 경우에만 level confidence를 무효화한다.
     */
    public void applyPreference(short level, boolean important) {
        if (skillLevel != level) {
            skillLevel = level;
            levelConfidence = null;
        }
        this.important = important;
    }
}

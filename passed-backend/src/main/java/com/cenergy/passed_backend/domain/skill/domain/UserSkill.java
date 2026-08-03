package com.cenergy.passed_backend.skill.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}

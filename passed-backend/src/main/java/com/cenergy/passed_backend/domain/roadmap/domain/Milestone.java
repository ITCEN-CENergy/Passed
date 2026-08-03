package com.cenergy.passed_backend.roadmap.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.skill.entity.Skill;
import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "milestones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Milestone extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "completion_criteria", columnDefinition = "text")
    private String completionCriteria;

    @Column(name = "difficulty", length = 50)
    private String difficulty;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "progress_rate", precision = 5, scale = 2)
    private BigDecimal progressRate;
}

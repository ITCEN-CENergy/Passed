package com.cenergy.passed_backend.domain.roadmap.domain;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.skill.entity.Skill;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "current_level", length = 20)
    private String currentLevel;

    @Column(name = "target_level", length = 20)
    private String targetLevel;

    @Column(name = "importance")
    private Integer importance;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "progress_rate", precision = 5, scale = 2)
    private BigDecimal progressRate;
}

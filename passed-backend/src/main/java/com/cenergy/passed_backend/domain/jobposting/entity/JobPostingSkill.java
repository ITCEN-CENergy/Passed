package com.cenergy.passed_backend.domain.jobposting.entity;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "job_posting_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_posting_skill",
                columnNames = {"job_posting_id", "skill_id"}
        ),
        indexes = {
                @Index(name = "idx_job_posting_skill_skill_id", columnList = "skill_id"),
                @Index(name = "idx_job_posting_skill_type", columnList = "skill_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingSkill extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", length = 30, nullable = false)
    private JobPostingSkillType skillType;

    @Column(name = "skill_level", nullable = false)
    private short skillLevel = 1;
}

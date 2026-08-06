package com.cenergy.passed_backend.domain.jobposting.entity;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingChunk;
import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.skill.entity.SkillMappingMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "job_posting_skill_evidences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_posting_skill_evidence_chunk",
                columnNames = {"job_posting_skill_id", "job_posting_chunk_id"}
        ),
        indexes = {
                @Index(name = "idx_job_skill_evidence_skill_id", columnList = "job_posting_skill_id"),
                @Index(name = "idx_job_skill_evidence_chunk_id", columnList = "job_posting_chunk_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingSkillEvidence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_skill_id", nullable = false)
    private JobPostingSkill jobPostingSkill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_chunk_id", nullable = false)
    private JobPostingChunk jobPostingChunk;

    @Column(name = "extracted_name", length = 100, nullable = false)
    private String extractedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_method", length = 30, nullable = false)
    private SkillMappingMethod mappingMethod;

    @Column(name = "mapping_similarity", precision = 4, scale = 3)
    private BigDecimal mappingSimilarity;
}

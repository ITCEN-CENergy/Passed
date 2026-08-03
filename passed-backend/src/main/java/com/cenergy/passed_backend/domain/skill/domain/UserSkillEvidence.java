package com.cenergy.passed_backend.skill.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.resume.entity.ResumeChunk;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "user_skill_evidences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_skill_evidence_chunk",
                columnNames = {"user_skill_id", "resume_chunk_id"}
        ),
        indexes = {
                @Index(name = "idx_user_skill_evidence_user_skill_id", columnList = "user_skill_id"),
                @Index(name = "idx_user_skill_evidence_resume_chunk_id", columnList = "resume_chunk_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkillEvidence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_skill_id", nullable = false)
    private UserSkill userSkill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_chunk_id", nullable = false)
    private ResumeChunk resumeChunk;

    @Column(name = "extracted_name", length = 100, nullable = false)
    private String extractedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_method", length = 30, nullable = false)
    private SkillMappingMethod mappingMethod;

    @Column(name = "mapping_similarity", precision = 4, scale = 3)
    private BigDecimal mappingSimilarity;
}

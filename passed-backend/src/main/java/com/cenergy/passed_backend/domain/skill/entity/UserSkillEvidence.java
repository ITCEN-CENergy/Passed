package com.cenergy.passed_backend.domain.skill.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterChunk;
import com.cenergy.passed_backend.domain.resume.entity.ResumeChunk;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "user_skill_evidences",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_skill_evidence_chunk",
                        columnNames = {"user_skill_id", "resume_chunk_id"}
                ),
                @UniqueConstraint(
                        name = "uk_user_skill_evidence_cover_letter_chunk",
                        columnNames = {"user_skill_id", "cover_letter_chunk_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_skill_evidence_user_skill_id", columnList = "user_skill_id"),
                @Index(name = "idx_user_skill_evidence_resume_chunk_id", columnList = "resume_chunk_id"),
                @Index(
                        name = "idx_user_skill_evidences_cover_letter_chunk_id",
                        columnList = "cover_letter_chunk_id"
                )
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_chunk_id")
    private ResumeChunk resumeChunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_letter_chunk_id")
    private CoverLetterChunk coverLetterChunk;

    @Column(name = "extracted_name", length = 100, nullable = false)
    private String extractedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_method", length = 30, nullable = false)
    private SkillMappingMethod mappingMethod;

    @Column(name = "mapping_similarity", precision = 4, scale = 3)
    private BigDecimal mappingSimilarity;

    @Column(name = "evidence_text", length = 500, nullable = false)
    private String evidenceText;

    @Column(name = "extracted_level", nullable = false)
    private short extractedLevel;

    @Column(name = "mapping_confidence", precision = 4, scale = 3, nullable = false)
    private BigDecimal mappingConfidence;
}

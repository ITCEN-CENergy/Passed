package com.cenergy.passed_backend.domain.coverletter.entity;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "cover_letter_feedbacks",
        indexes = {
                @Index(name = "idx_cover_letter_feedbacks_cover_letter_id", columnList = "cover_letter_id"),
                @Index(name = "idx_cover_letter_feedbacks_job_posting_id", columnList = "job_posting_id"),
                @Index(name = "idx_cover_letter_feedbacks_created_at", columnList = "created_at")
        }
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_feedback_company",
                columnNames = "cover_letter_company_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterFeedback extends CreatedAtEntity {
public class CoverLetterFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_id", nullable = false, unique = true)
    private CoverLetterCompany coverLetterCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;
    @Convert(converter = CoverLetterScoreConverter.class)
    @Column(name = "overall_score")
    private CoverLetterScore overallScore;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "ai_model", length = 100)
    private String aiModel;
}

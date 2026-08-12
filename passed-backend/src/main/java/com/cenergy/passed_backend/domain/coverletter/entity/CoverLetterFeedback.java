package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stores one overall AI-feedback result for one company-specific cover letter.
 * The migration makes cover_letter_company_id unique, so this entity intentionally has no direct job-posting field.
 */
@Getter
@Entity
@Table(
        name = "cover_letter_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_feedback_company",
                columnNames = "cover_letter_company_id"
        ),
        indexes = @Index(name = "idx_cover_letter_feedbacks_created_at", columnList = "created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterFeedback extends BaseTimeEntity {

    /** Database-generated feedback identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The company-specific cover letter that owns this single overall feedback result. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_id", nullable = false, unique = true)
    private CoverLetterCompany coverLetterCompany;

    /** The qualitative overall score persisted through the converter as a Korean VARCHAR label. */
    @Convert(converter = CoverLetterScoreConverter.class)
    @Column(name = "overall_score", length = 10)
    private CoverLetterScore overallScore;

    /** The overall natural-language feedback from the AI. */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "text")
    private String improvements;

    /** The model identifier used to generate the feedback. */
    @Column(name = "ai_model", length = 100)
    private String aiModel;

    /** Creates a new overall feedback result bound to one company-specific cover letter. */
    public static CoverLetterFeedback create(
            CoverLetterCompany coverLetterCompany,
            CoverLetterScore overallScore,
            String summary,
            String strengths,
            String improvements,
            String aiModel
    ) {
        CoverLetterFeedback value = new CoverLetterFeedback();
        value.coverLetterCompany = coverLetterCompany;
        value.update(overallScore, summary, strengths, improvements, aiModel);
        return value;
    }

    public void update(
            CoverLetterScore overallScore,
            String summary,
            String strengths,
            String improvements,
            String aiModel
    ) {
        this.overallScore = overallScore;
        this.summary = summary;
        this.strengths = strengths;
        this.improvements = improvements;
        this.aiModel = aiModel;
    }
}

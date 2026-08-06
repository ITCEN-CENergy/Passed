package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cover_letter_feedbacks",
        indexes = {
                @Index(name = "idx_cover_letter_feedbacks_created_at", columnList = "created_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_feedback_company",
                columnNames = "cover_letter_company_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_id", nullable = false, unique = true)
    private CoverLetterCompany coverLetterCompany;

    @Convert(converter = CoverLetterScoreConverter.class)
    @Column(name = "overall_score")
    private CoverLetterScore overallScore;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "ai_model", length = 100)
    private String aiModel;
}

package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cover_letter_item_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_item_feedback_company_item",
                columnNames = "cover_letter_company_item_id"
        ),
        indexes = @Index(
                name = "uk_cover_letter_item_feedback_company_item",
                columnList = "cover_letter_company_item_id",
                unique = true
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterItemFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_item_id", nullable = false, unique = true)
    private CoverLetterCompanyItem coverLetterCompanyItem;

    @Convert(converter = CoverLetterScoreConverter.class)
    @Column(name = "score")
    private CoverLetterScore score;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "text")
    private String improvements;

    @Column(name = "suggested_answer", columnDefinition = "text")
    private String suggestedAnswer;

    public static CoverLetterItemFeedback create(
            CoverLetterCompanyItem item,
            CoverLetterScore score,
            String strengths,
            String improvements,
            String suggestedAnswer
    ) {
        CoverLetterItemFeedback value = new CoverLetterItemFeedback();
        value.coverLetterCompanyItem = item;
        value.update(score, strengths, improvements, suggestedAnswer);
        return value;
    }

    public void update(
            CoverLetterScore score,
            String strengths,
            String improvements,
            String suggestedAnswer
    ) {
        this.score = score;
        this.strengths = strengths;
        this.improvements = improvements;
        this.suggestedAnswer = suggestedAnswer;
    }
}

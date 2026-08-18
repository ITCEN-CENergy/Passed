package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stores one AI-feedback result for one company-specific cover-letter item.
 * The unique foreign key allows feedback to be replaced after a regeneration instead of creating stale history rows.
 */
@Getter
@Entity
@Table(
        name = "cover_letter_item_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_item_feedback_company_item",
                columnNames = "cover_letter_company_item_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterItemFeedback extends BaseTimeEntity {

    /** Database-generated item-feedback identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The company-specific item that owns this single feedback result. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_item_id", nullable = false, unique = true)
    private CoverLetterCompanyItem coverLetterCompanyItem;

    /** The qualitative item score persisted through the converter as a Korean VARCHAR label. */
    @Convert(converter = CoverLetterScoreConverter.class)
    @Column(name = "score", length = 10)
    private CoverLetterScore score;

    /** Shortcomings identified by the AI. The legacy column name is retained for schema compatibility. */
    @Column(name = "strengths", columnDefinition = "text")
    private String shortcomings;

    /** Actionable revision direction. The legacy column name is retained for schema compatibility. */
    @Column(name = "improvements", columnDefinition = "text")
    private String recommendedRevisionDirection;

    /** The AI's suggested revised answer. */
    @Column(name = "suggested_answer", columnDefinition = "text")
    private String suggestedAnswer;

    /** Creates the first feedback result for one company-specific cover-letter item. */
    public static CoverLetterItemFeedback create(
            CoverLetterCompanyItem item,
            CoverLetterScore score,
            String shortcomings,
            String recommendedRevisionDirection,
            String suggestedAnswer
    ) {
        CoverLetterItemFeedback value = new CoverLetterItemFeedback();
        value.coverLetterCompanyItem = item;
        value.update(score, shortcomings, recommendedRevisionDirection, suggestedAnswer);
        return value;
    }

    /** Replaces the mutable AI-feedback fields during feedback regeneration. */
    public void update(
            CoverLetterScore score,
            String shortcomings,
            String recommendedRevisionDirection,
            String suggestedAnswer
    ) {
        this.score = score;
        this.shortcomings = shortcomings;
        this.recommendedRevisionDirection = recommendedRevisionDirection;
        this.suggestedAnswer = suggestedAnswer;
    }
}

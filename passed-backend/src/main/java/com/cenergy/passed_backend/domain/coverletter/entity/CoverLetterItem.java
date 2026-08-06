package com.cenergy.passed_backend.domain.coverletter.entity;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestion;
import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "cover_letter_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_item_question",
                columnNames = {"cover_letter_id", "question_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_id", nullable = false)
    private CoverLetter coverLetter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private CoverLetterQuestion question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "relevance_score", precision = 4, scale = 2)
    private BigDecimal relevanceScore;
}

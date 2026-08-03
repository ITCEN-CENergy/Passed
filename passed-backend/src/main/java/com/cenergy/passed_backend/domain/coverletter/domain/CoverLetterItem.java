package com.cenergy.passed_backend.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}

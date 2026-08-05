package com.cenergy.passed_backend.domain.coverletter.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "cover_letter_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 30, nullable = false, unique = true)
    private CoverLetterQuestionType questionType;

    @Column(name = "question_text", length = 500, nullable = false)
    private String questionText;

    @Column(name = "guide_text", columnDefinition = "text")
    private String guideText;

    @Column(name = "match_weight", precision = 3, scale = 2)
    private BigDecimal matchWeight;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}

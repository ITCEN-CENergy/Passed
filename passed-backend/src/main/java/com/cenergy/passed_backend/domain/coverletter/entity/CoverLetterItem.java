package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 공통 자기소개서(cover_letters)의 문항 엔티티.
 * 공통 질문 마스터(cover_letter_questions)에 답변을 연결하며, 한 자기소개서 안에서
 * 같은 질문을 중복해 가질 수 없도록 (cover_letter_id, question_id) 유일 제약을 둔다.
 * 이번 작업의 CRUD 대상은 공고별 자기소개서(CoverLetterCompanyItem)이므로
 * 여기서는 병합 충돌만 정리한다.
 */
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
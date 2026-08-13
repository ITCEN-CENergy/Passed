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
 * 공통 자기소개서 작성 API가 질문별 답변을 생성하고 갱신한다.
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
    // 조윤지: 자기소개서 문항별 답변에 대한 관련도 점수. 자기소개서 작성 API가 질문별 답변을 생성하고 갱신할 때, 관련도 점수를 계산하여 저장한다.
    public static CoverLetterItem create(
            CoverLetter coverLetter,
            CoverLetterQuestion question,
            String answer
    ) {
        CoverLetterItem item = new CoverLetterItem();
        item.coverLetter = coverLetter;
        item.question = question;
        item.answer = answer;
        return item;
    }

    /** 답변이 바뀌면 이전 답변에 대한 관련도 점수는 더 이상 유효하지 않다. */
    public void updateAnswer(String answer) {
        if (!java.util.Objects.equals(this.answer, answer)) {
            this.answer = answer;
            this.relevanceScore = null;
        }
    }
}

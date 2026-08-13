package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공고별 자기소개서의 개별 문항과 사용자가 작성한 답변을 보관한다.
 * 문항 순서는 부모 자기소개서 안에서 유일하며, 실제 중복 방지는 DB 유일 제약으로도 수행한다.
 */
@Entity
@Getter
@Table(
        name = "cover_letters_company_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letters_company_item_order",
                columnNames = {"cover_letter_company_id", "display_order"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterCompanyItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_company_id", nullable = false)
    private CoverLetterCompany coverLetterCompany;

    @Column(name = "question_text", length = 1000, nullable = false)
    private String questionText;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "character_limit")
    private Integer characterLimit;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 새 문항을 생성한다.
     * 생성과 수정에서 같은 검증을 사용하도록 값 설정을 update 메서드에 위임한다.
     */
    public static CoverLetterCompanyItem create(
            CoverLetterCompany coverLetterCompany,
            String questionText,
            String answer,
            Integer characterLimit,
            int displayOrder
    ) {
        CoverLetterCompanyItem value = new CoverLetterCompanyItem();
        value.coverLetterCompany = coverLetterCompany;
        value.update(questionText, answer, characterLimit, displayOrder);
        return value;
    }

    /**
     * 문항의 질문·답변·글자 수 제한·노출 순서를 한 번에 변경한다.
     * 답변은 빈 초안 상태를 허용하지만 질문과 순서는 반드시 유효해야 한다.
     */
    public void update(String questionText, String answer, Integer characterLimit, int displayOrder) {
        if (questionText == null || questionText.isBlank()) {
            throw new IllegalArgumentException("questionText must not be blank");
        }
        if (characterLimit != null && characterLimit <= 0) {
            throw new IllegalArgumentException("characterLimit must be positive");
        }
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
        if (answer != null && characterLimit != null && answer.length() > characterLimit) {
            throw new IllegalArgumentException("answer exceeds characterLimit");
        }
        this.questionText = questionText.trim();
        this.answer = answer;
        this.characterLimit = characterLimit;
        this.displayOrder = displayOrder;
    }

    /** 일괄 편집 중 순서 교환이 DB 유일 제약과 충돌하지 않도록 양수 임시 순서를 부여한다. */
    public void prepareDisplayOrder(int temporaryDisplayOrder) {
        if (temporaryDisplayOrder < 1) {
            throw new IllegalArgumentException("temporaryDisplayOrder must be positive");
        }
        this.displayOrder = temporaryDisplayOrder;
    }
}

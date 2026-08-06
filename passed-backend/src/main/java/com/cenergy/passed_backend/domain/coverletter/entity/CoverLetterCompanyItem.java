package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}

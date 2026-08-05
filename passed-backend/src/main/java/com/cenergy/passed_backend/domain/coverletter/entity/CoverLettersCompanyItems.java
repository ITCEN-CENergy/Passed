package com.cenergy.passed_backend.domain.coverletter.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "cover_letters_company_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLettersCompanyItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "cover_letter_company_id")
    private CoverLettersCompany coverLetterCompany;

    @Column
    private String questionText;

    @Column
    private String answer;

    @Column
    private Long character_limit;

    @Column
    private int display_order;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

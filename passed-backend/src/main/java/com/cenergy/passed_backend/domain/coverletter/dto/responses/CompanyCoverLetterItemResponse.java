package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;

import java.time.OffsetDateTime;

/**
 * 공고별 자기소개서 문항을 클라이언트에 전달하는 응답이다.
 * entity를 직접 직렬화하지 않아 지연 연관과 내부 필드가 외부 계약에 노출되지 않는다.
 */
public record CompanyCoverLetterItemResponse(
        Long id,
        String questionText,
        String answer,
        Integer characterLimit,
        int displayOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** 저장된 문항 엔티티를 API 응답 형태로 변환한다. */
    public static CompanyCoverLetterItemResponse from(CoverLetterCompanyItem item) {
        return new CompanyCoverLetterItemResponse(
                item.getId(),
                item.getQuestionText(),
                item.getAnswer(),
                item.getCharacterLimit(),
                item.getDisplayOrder(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

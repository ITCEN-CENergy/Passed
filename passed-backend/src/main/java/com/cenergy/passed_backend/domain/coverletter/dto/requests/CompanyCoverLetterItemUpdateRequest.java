package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 기존 공고별 자기소개서 문항을 교체하는 요청이다.
 * 부분 수정으로 문항을 재생성하지 않아 문항 ID와 첨삭 연결의 추적 가능성을 유지한다.
 */
public record CompanyCoverLetterItemUpdateRequest(
        /** 수정된 질문 문구다. */
        @NotBlank @Size(max = 1000) String questionText,
        /** 수정된 답변이며 빈 초안 상태를 허용한다. */
        String answer,
        /** 수정된 답변 최대 글자 수이며 값이 있으면 양수여야 한다. */
        @Positive Integer characterLimit,
        /** 수정된 화면 노출 순서이며 1 이상이어야 한다. */
        @Positive int displayOrder
) {
}

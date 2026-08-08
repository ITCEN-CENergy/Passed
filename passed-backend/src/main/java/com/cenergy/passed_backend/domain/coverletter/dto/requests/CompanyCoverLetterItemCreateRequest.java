package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 공고별 자기소개서에 새 문항을 추가할 때 사용하는 요청이다.
 * answer는 빈 초안 상태를 허용하며, 실제 글자 수 제한 검증은 서비스에서 수행한다.
 */
public record CompanyCoverLetterItemCreateRequest(
        /** 사용자에게 보여 줄 질문 문구다. */
        @NotBlank @Size(max = 1000) String questionText,
        /** 사용자가 작성한 답변이며 빈 문자열 또는 null 초안을 허용한다. */
        String answer,
        /** 값이 존재하면 답변 최대 글자 수를 뜻하는 양수다. */
        @Positive Integer characterLimit,
        /** 같은 자기소개서 안에서 1부터 시작하는 문항 표시 순서다. */
        @Positive int displayOrder
) {
}

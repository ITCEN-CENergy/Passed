package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 현재 사용자가 특정 채용공고용 자기소개서를 처음 만들 때 받는 요청이다.
 * userId는 인증 컨텍스트에서만 얻고 요청 본문에는 절대 포함하지 않는다.
 */
public record CompanyCoverLetterCreateRequest(
        /** 자기소개서가 연결될 기존 채용공고 ID다. */
        @NotNull @Positive Long jobPostingId,
        /** 사용자가 화면에서 구분할 자기소개서 제목이다. */
        @NotBlank @Size(max = 255) String title,
        /** 생성 시 함께 저장할 문항 목록이며 빈 목록은 허용하지 않는다. */
        @NotEmpty List<@Valid CompanyCoverLetterItemCreateRequest> items
) {
}

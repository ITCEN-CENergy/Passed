package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공고별 자기소개서의 제목만 변경하는 요청이다.
 * 문항은 전용 문항 API로 수정해 기존 문항 ID와 첨삭 연관을 보존한다.
 */
public record CompanyCoverLetterUpdateRequest(
        /** 공백이 아닌 최대 255자 제목이다. */
        @NotBlank @Size(max = 255) String title
) {
}

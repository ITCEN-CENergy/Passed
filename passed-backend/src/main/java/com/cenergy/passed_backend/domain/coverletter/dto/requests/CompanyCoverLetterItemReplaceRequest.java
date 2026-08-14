package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 편집 화면이 보내는 문항 한 건이다. ID가 없으면 새 문항으로 취급한다. */
public record CompanyCoverLetterItemReplaceRequest(
        @Positive Long id,
        @NotBlank @Size(max = 1000) String questionText,
        String answer,
        @Positive Integer characterLimit,
        @Positive int displayOrder
) {
}

package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 공통 자기소개서의 현재 질문별 답변 전체를 전달한다. */
public record CommonCoverLetterUpsertRequest(
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull @Positive Long questionId,
            @NotBlank @Size(max = 5000) String answer
    ) {
    }
}

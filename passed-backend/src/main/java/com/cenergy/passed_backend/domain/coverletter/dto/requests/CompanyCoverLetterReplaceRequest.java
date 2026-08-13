package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 편집 화면의 자기소개서 제목, 공고 정보, 전체 문항을 원자적으로 저장하는 요청이다. */
public record CompanyCoverLetterReplaceRequest(
        @NotBlank @Size(max = 255) String title,
        @Valid ManualJobPostingRequest jobPosting,
        @NotEmpty @Size(max = 30) List<@Valid CompanyCoverLetterItemReplaceRequest> items
) {
}

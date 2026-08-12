package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 직접 입력한 공고와 최초 자기소개서 문항을 한 트랜잭션으로 생성하는 요청이다. */
public record ManualCompanyCoverLetterCreateRequest(
        @Size(max = 255) String title,
        @NotNull @Valid ManualJobPostingRequest jobPosting,
        @NotEmpty List<@Valid CompanyCoverLetterItemCreateRequest> items
) {
}

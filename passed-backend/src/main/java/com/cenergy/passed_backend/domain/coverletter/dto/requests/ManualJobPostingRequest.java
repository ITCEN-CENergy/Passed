package com.cenergy.passed_backend.domain.coverletter.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 자기소개서 목록에서 직접 입력하는 채용공고 정보다. */
public record ManualJobPostingRequest(
        @NotBlank @Size(max = 255) String postingTitle,
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String jobRoleName,
        String positionDetail,
        @Size(max = 50) String careerType,
        @Size(max = 255) String hireType,
        String mainDuty,
        String qualification,
        String preference
) {
}

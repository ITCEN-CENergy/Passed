package com.cenergy.passed_backend.domain.jobposting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobPostingCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Positive Long companyId,
        @NotNull @Positive Long jobRoleId,
        @Pattern(regexp = "^\\d{8}$") String startYmd,
        @Pattern(regexp = "^\\d{8}$") String endYmd,
        @Positive Integer headcount,
        @Size(max = 50) String careerType,
        @Size(max = 255) String hireType,
        @Size(max = 255) String region,
        @Size(max = 255) String educationLevel,
        String positionDetail,
        String mainDuty,
        String qualification,
        String preference,
        String disqualification,
        String process,
        @NotEmpty @Valid List<JobPostingSkillCreateRequest> requiredSkills,
        @NotNull @Valid List<JobPostingSkillCreateRequest> preferredSkills
) {
    public JobPostingCreateRequest {
        requiredSkills = requiredSkills == null ? null : List.copyOf(requiredSkills);
        preferredSkills = preferredSkills == null ? null : List.copyOf(preferredSkills);
    }
}

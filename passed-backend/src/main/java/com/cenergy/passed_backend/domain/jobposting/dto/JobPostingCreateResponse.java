package com.cenergy.passed_backend.domain.jobposting.dto;

public record JobPostingCreateResponse(
        Long jobPostingId,
        int requiredSkillCount,
        int preferredSkillCount
) {
}

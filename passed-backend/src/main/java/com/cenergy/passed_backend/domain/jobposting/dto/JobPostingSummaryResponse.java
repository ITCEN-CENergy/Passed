package com.cenergy.passed_backend.domain.jobposting.dto;

public record JobPostingSummaryResponse(
        Long jobPostingId,
        String title,
        String region,
        String companyName,
        String companySize,
        String jobRoleName,
        String industryName,
        boolean matched
) {
}

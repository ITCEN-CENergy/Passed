package com.cenergy.passed_backend.domain.recommendation.dto;

public record JobPostingSummaryResponse(
        Long jobPostingId,
        String title,
        String region,
        String companyName,
        String jobRoleName,
        String industryName
) {
}

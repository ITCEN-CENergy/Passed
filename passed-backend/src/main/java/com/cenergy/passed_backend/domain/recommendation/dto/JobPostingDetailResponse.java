package com.cenergy.passed_backend.domain.recommendation.dto;

public record JobPostingDetailResponse(
        Long jobPostingId,
        String title,
        String industryName,
        String jobRoleName,
        String companyName,
        String companySize,
        String region,
        String careerType,
        String hireType,
        String educationLevel,
        String positionDetail,
        String mainDuty,
        String qualification,
        String preference,
        String disqualification,
        String process,
        String benefit
) {
}

package com.cenergy.passed_backend.domain.jobposting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;

public record JobPostingListRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        String keyword,
        String region,
        Long industryId,
        Long jobRoleId,
        CompanySize companySize,
        Boolean matchedOnly
) {
    public JobPostingListRequest {
        page = page == null ? 0 : page;
        size = size == null ? 12 : size;
        keyword = normalize(keyword);
        region = normalize(region);
        matchedOnly = Boolean.TRUE.equals(matchedOnly);
    }

    public JobPostingListRequest(Integer page, Integer size) {
        this(page, size, null, null, null, null, null, false);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.cenergy.passed_backend.domain.jobposting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record JobPostingListRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public JobPostingListRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}

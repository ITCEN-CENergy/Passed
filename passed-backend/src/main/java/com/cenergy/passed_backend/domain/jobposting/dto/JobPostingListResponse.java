package com.cenergy.passed_backend.domain.jobposting.dto;


import java.util.List;

public record JobPostingListResponse(
        List<JobPostingSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public JobPostingListResponse {
        content = List.copyOf(content);
    }
}
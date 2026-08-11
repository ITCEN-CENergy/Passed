package com.cenergy.passed_backend.domain.user.dto;

import java.util.List;

public record IndustryListResponse(List<IndustryResponse> industries) {
    public IndustryListResponse {
        industries = List.copyOf(industries);
    }
}

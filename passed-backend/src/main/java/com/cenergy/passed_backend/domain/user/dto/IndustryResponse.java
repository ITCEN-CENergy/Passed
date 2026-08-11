package com.cenergy.passed_backend.domain.user.dto;

import com.cenergy.passed_backend.domain.jobposting.entity.Industry;

public record IndustryResponse(Long id, String name) {
    public static IndustryResponse from(Industry industry) {
        return new IndustryResponse(industry.getId(), industry.getIndustryName());
    }
}

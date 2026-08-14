package com.cenergy.passed_backend.domain.jobposting.dto;

import java.util.List;

public record JobPostingCreateOptionsResponse(
        List<JobPostingNamedOptionResponse> companies,
        List<JobPostingNamedOptionResponse> skills
) {
    public JobPostingCreateOptionsResponse {
        companies = List.copyOf(companies);
        skills = List.copyOf(skills);
    }
}

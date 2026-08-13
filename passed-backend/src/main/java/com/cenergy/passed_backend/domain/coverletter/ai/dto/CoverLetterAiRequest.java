package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverLetterAiRequest(
        String question,
        String content,
        @JsonProperty("job_description") String jobDescription
) {
}

package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverLetterSuggestionResponse(
        @JsonProperty("suggested_answer") String suggestedAnswer
) {
}

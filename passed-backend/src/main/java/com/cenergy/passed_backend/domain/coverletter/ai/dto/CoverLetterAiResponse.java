package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverLetterAiResponse(
        @JsonProperty("qa_alignment_score") Integer qaAlignmentScore,
        String shortcomings,
        @JsonProperty("recommended_revision_direction") String recommendedRevisionDirection
) {
}

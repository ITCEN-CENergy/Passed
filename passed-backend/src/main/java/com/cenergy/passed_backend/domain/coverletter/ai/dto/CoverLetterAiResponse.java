package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverLetterAiResponse(
        @JsonProperty("spell_checked_content") String spellCheckedContent,
        @JsonProperty("qa_alignment_score") Integer qaAlignmentScore,
        @JsonProperty("qa_alignment_feedback") String qaAlignmentFeedback,
        @JsonProperty("jd_fit_feedback") String jobFitFeedback,
        @JsonProperty("final_edited_content") String finalEditedContent
) {
}

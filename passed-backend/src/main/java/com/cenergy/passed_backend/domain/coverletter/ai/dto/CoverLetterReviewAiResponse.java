package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CoverLetterReviewAiResponse(
        @JsonProperty("overall_feedback") OverallFeedback overallFeedback,
        List<ItemFeedback> items
) {
    public record OverallFeedback(
            @JsonProperty("overall_score") Integer overallScore,
            String summary,
            String strengths,
            String improvements
    ) {
    }

    public record ItemFeedback(
            @JsonProperty("item_id") Long itemId,
            @JsonProperty("display_order") Integer displayOrder,
            @JsonProperty("qa_alignment_score") Integer qaAlignmentScore,
            @JsonProperty("qa_alignment_feedback") String qaAlignmentFeedback,
            @JsonProperty("jd_fit_feedback") String jobFitFeedback
    ) {
    }
}

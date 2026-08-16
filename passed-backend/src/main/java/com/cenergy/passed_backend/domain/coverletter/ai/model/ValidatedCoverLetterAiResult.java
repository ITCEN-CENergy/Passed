package com.cenergy.passed_backend.domain.coverletter.ai.model;

public record ValidatedCoverLetterAiResult(
        int qaAlignmentScore,
        String qaAlignmentFeedback,
        String jobFitFeedback
) {
}

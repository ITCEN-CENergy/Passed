package com.cenergy.passed_backend.domain.coverletter.ai.model;

public record ValidatedCoverLetterAiResult(
        String spellCheckedContent,
        int qaAlignmentScore,
        String qaAlignmentFeedback,
        String jobFitFeedback,
        String finalEditedContent
) {
}

package com.cenergy.passed_backend.domain.coverletter.application;

public record CoverLetterFeedbackInput(
        Long itemId,
        String question,
        String answer,
        Integer characterLimit,
        String jobDescription
) {
}

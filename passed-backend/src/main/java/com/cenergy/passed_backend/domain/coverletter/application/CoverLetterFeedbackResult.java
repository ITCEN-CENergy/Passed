package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;

import java.time.OffsetDateTime;

public record CoverLetterFeedbackResult(
        Long id,
        Long companyCoverLetterItemId,
        CoverLetterScore score,
        String shortcomings,
        String recommendedRevisionDirection,
        String suggestedAnswer,
        Integer characterLimit,
        int suggestedAnswerLength,
        boolean withinCharacterLimit,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

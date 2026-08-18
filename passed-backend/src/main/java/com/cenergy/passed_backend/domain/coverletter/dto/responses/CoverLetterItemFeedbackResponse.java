package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.application.CoverLetterFeedbackResult;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;

import java.time.OffsetDateTime;

public record CoverLetterItemFeedbackResponse(
        Long id,
        Long companyCoverLetterItemId,
        CoverLetterScore score,
        String scoreLabel,
        String shortcomings,
        String recommendedRevisionDirection,
        String suggestedAnswer,
        Integer characterLimit,
        int suggestedAnswerLength,
        boolean withinCharacterLimit,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CoverLetterItemFeedbackResponse from(CoverLetterFeedbackResult result) {
        return new CoverLetterItemFeedbackResponse(
                result.id(),
                result.companyCoverLetterItemId(),
                result.score(),
                result.score() == null ? null : result.score().getDatabaseValue(),
                result.shortcomings(),
                result.recommendedRevisionDirection(),
                result.suggestedAnswer(),
                result.characterLimit(),
                result.suggestedAnswerLength(),
                result.withinCharacterLimit(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}

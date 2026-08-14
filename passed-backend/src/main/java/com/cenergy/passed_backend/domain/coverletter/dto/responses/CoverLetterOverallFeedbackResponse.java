package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterFeedback;
import java.time.OffsetDateTime;
import java.util.List;

public record CoverLetterOverallFeedbackResponse(
        Long id,
        Long coverLetterId,
        String score,
        String scoreLabel,
        String summary,
        String strengths,
        String improvements,
        OffsetDateTime updatedAt,
        List<CoverLetterItemFeedbackResponse> items
) {
    public static CoverLetterOverallFeedbackResponse from(
            CoverLetterFeedback feedback,
            List<CoverLetterItemFeedbackResponse> items
    ) {
        return new CoverLetterOverallFeedbackResponse(
                feedback.getId(),
                feedback.getCoverLetterCompany().getId(),
                feedback.getOverallScore().name(),
                feedback.getOverallScore().getDatabaseValue(),
                feedback.getSummary(),
                feedback.getStrengths(),
                feedback.getImprovements(),
                feedback.getUpdatedAt(),
                items
        );
    }
}

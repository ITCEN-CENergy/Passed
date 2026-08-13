package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestion;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestionType;

public record CoverLetterQuestionResponse(
        Long questionId,
        CoverLetterQuestionType questionType,
        String questionText,
        String guideText,
        Integer displayOrder
) {
    public static CoverLetterQuestionResponse from(CoverLetterQuestion value) {
        return new CoverLetterQuestionResponse(value.getId(), value.getQuestionType(),
                value.getQuestionText(), value.getGuideText(), value.getDisplayOrder());
    }
}

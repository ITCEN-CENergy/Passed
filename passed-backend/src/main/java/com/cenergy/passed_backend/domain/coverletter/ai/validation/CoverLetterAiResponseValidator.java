package com.cenergy.passed_backend.domain.coverletter.ai.validation;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiResponse;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CoverLetterAiResponseValidator {

    public ValidatedCoverLetterAiResult validate(CoverLetterAiResponse response) {
        invalidIf(response == null, "cover letter AI response must not be null");
        invalidIf(response.qaAlignmentScore() == null
                        || response.qaAlignmentScore() < 0
                        || response.qaAlignmentScore() > 100,
                "qa_alignment_score must be between 0 and 100");
        requireText(response.spellCheckedContent(), "spell_checked_content");
        requireText(response.qaAlignmentFeedback(), "qa_alignment_feedback");
        invalidIf(response.jobFitFeedback() == null, "jd_fit_feedback must not be null");
        requireText(response.finalEditedContent(), "final_edited_content");

        return new ValidatedCoverLetterAiResult(
                response.spellCheckedContent(),
                response.qaAlignmentScore(),
                response.qaAlignmentFeedback(),
                response.jobFitFeedback(),
                response.finalEditedContent()
        );
    }

    private void requireText(String value, String field) {
        invalidIf(value == null || value.isBlank(), field + " must not be blank");
    }

    private void invalidIf(boolean invalid, String message) {
        if (invalid) {
            throw new CoverLetterAiException(ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE, message);
        }
    }
}

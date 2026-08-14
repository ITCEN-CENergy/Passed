package com.cenergy.passed_backend.domain.coverletter.ai.validation;

import com.cenergy.passed_backend.domain.coverletter.ai.exception.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiResponse;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * AI 서버로부터 받은 자기소개서 첨삭 응답(Response)의 유효성을 검증하는 클래스입니다.
 * 필수 텍스트 필드의 누락 여부나 점수 범위 등을 체크하고, 검증된 모델로 변환하여 반환합니다.
 */
@Component
public class CoverLetterAiResponseValidator {

    public ValidatedCoverLetterAiResult validate(CoverLetterAiResponse response) {
        invalidIf(response == null, "cover letter AI response must not be null");
        invalidIf(response.qaAlignmentScore() == null
                        || response.qaAlignmentScore() < 0
                        || response.qaAlignmentScore() > 100,
                "qa_alignment_score must be between 0 and 100");
        requireText(response.qaAlignmentFeedback(), "qa_alignment_feedback");
        invalidIf(response.jobFitFeedback() == null, "jd_fit_feedback must not be null");
        requireText(response.finalEditedContent(), "final_edited_content");

        return new ValidatedCoverLetterAiResult(
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

package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.exception.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CoverLetterScorePolicy {

    public CoverLetterScore from(int score) {
        if (score < 0 || score > 100) {
            throw new CoverLetterAiException(
                    ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE,
                    "cover letter score must be between 0 and 100"
            );
        }
        if (score >= 80) {
            return CoverLetterScore.SUFFICIENT;
        }
        if (score >= 60) {
            return CoverLetterScore.INSUFFICIENT;
        }
        return CoverLetterScore.DEFICIENT;
    }
}

package com.cenergy.passed_backend.domain.skillgap.ai.client;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class LearningCompetencyAiException extends RuntimeException {
    private final ErrorCode errorCode;

    public LearningCompetencyAiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LearningCompetencyAiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

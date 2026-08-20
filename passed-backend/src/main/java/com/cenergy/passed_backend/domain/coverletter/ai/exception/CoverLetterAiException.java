package com.cenergy.passed_backend.domain.coverletter.ai.exception;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class CoverLetterAiException extends RuntimeException {
    private final ErrorCode errorCode;

    public CoverLetterAiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CoverLetterAiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

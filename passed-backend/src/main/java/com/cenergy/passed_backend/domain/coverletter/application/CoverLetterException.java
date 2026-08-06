package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class CoverLetterException extends RuntimeException {
    private final ErrorCode errorCode;

    public CoverLetterException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

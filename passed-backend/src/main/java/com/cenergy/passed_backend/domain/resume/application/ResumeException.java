package com.cenergy.passed_backend.domain.resume.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class ResumeException extends RuntimeException {
    private final ErrorCode errorCode;

    public ResumeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ResumeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

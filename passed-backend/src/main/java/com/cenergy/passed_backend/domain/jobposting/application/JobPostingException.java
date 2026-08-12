package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class JobPostingException extends RuntimeException {
    private final ErrorCode errorCode;

    public JobPostingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

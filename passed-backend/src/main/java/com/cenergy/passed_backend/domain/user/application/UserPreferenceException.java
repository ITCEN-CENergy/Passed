package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class UserPreferenceException extends RuntimeException {
    private final ErrorCode errorCode;

    public UserPreferenceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class UserSkillException extends RuntimeException {
    private final ErrorCode errorCode;

    public UserSkillException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.cenergy.passed_backend.domain.skill.ai.client;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class UserSkillAiException extends RuntimeException {
    private final ErrorCode errorCode;

    public UserSkillAiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public UserSkillAiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

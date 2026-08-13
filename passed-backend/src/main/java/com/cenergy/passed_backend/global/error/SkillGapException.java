package com.cenergy.passed_backend.global.error;

public class SkillGapException extends RuntimeException {
    private final ErrorCode errorCode;

    public SkillGapException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

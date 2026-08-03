package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class RoadmapException extends RuntimeException {
    private final ErrorCode errorCode;

    public RoadmapException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

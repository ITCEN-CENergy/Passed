package com.cenergy.passed_backend.domain.roadmap.ai.client;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class RoadmapAiException extends RuntimeException {
    private final ErrorCode errorCode;

    public RoadmapAiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RoadmapAiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

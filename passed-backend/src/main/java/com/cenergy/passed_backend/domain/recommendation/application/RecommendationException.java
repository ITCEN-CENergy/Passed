package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class RecommendationException extends RuntimeException {
    private final ErrorCode errorCode;

    public RecommendationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

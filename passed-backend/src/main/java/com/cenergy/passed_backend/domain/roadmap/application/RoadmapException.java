package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.global.error.ErrorCode;

public class RoadmapException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Long roadmapId;

    public RoadmapException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public RoadmapException(ErrorCode errorCode, String message, Long roadmapId) {
        super(message);
        this.errorCode = errorCode;
        this.roadmapId = roadmapId;
    }

    public static RoadmapException duplicate(Long roadmapId) {
        return new RoadmapException(
                ErrorCode.ROADMAP_ALREADY_EXISTS,
                "An active roadmap already exists for the same job postings",
                roadmapId);
    }

    public static RoadmapException generationInProgress(Long roadmapId) {
        return new RoadmapException(
                ErrorCode.ROADMAP_GENERATION_IN_PROGRESS,
                "Roadmap generation is already in progress",
                roadmapId);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRoadmapId() {
        return roadmapId;
    }
}

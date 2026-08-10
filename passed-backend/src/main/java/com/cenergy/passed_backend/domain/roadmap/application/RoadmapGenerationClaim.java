package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;

public record RoadmapGenerationClaim(Long roadmapId, RoadmapStatus status, boolean acquired) {
    public static RoadmapGenerationClaim acquired(Long roadmapId) {
        return new RoadmapGenerationClaim(roadmapId, RoadmapStatus.CREATING, true);
    }

    public static RoadmapGenerationClaim existing(Long roadmapId, RoadmapStatus status) {
        return new RoadmapGenerationClaim(roadmapId, status, false);
    }
}

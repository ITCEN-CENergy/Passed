package com.cenergy.passed_backend.domain.recommendation.dto;

import java.util.List;

public record RecommendationUserSkillsResponse(Long runId, List<UserSkillSnapshotResponse> skills) {
    public RecommendationUserSkillsResponse {
        skills = List.copyOf(skills);
    }
}

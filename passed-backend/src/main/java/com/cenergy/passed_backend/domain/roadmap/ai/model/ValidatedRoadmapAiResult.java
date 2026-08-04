package com.cenergy.passed_backend.domain.roadmap.ai.model;

import java.util.List;

public record ValidatedRoadmapAiResult(
        String title,
        List<ValidatedRoadmapSkill> skills
) {
    public ValidatedRoadmapAiResult {
        skills = List.copyOf(skills);
    }
}

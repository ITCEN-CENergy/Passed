package com.cenergy.passed_backend.domain.roadmap.ai.model;

import java.util.List;

public record ValidatedLearningResource(
        String resourceId,
        String resourceType,
        String title,
        String description,
        String provider,
        String url,
        String thumbnailUrl,
        List<String> authors,
        Boolean isFree
) {
    public ValidatedLearningResource {
        authors = List.copyOf(authors);
    }
}

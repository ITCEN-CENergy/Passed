package com.cenergy.passed_backend.domain.roadmap.api;

import jakarta.validation.constraints.Size;

public record RoadmapReplanPreviewRequest(
        @Size(max = 500) String userInstruction
) {
}

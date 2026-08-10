package com.cenergy.passed_backend.domain.roadmap.dto;

import jakarta.validation.constraints.Size;

public record RoadmapReplanPreviewRequest(
        @Size(max = 500) String userInstruction
) {
}

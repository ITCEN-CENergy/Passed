package com.cenergy.passed_backend.domain.roadmap.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record RoadmapGenerateRequest(
        @NotEmpty List<@NotNull @Positive Long> jobPostingIds
) {
}

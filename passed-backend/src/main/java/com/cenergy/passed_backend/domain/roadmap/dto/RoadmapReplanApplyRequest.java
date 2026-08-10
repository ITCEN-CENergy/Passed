package com.cenergy.passed_backend.domain.roadmap.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RoadmapReplanApplyRequest(@NotNull UUID replanToken) {
}

package com.cenergy.passed_backend.domain.roadmap.api;

import jakarta.validation.constraints.NotNull;

public record MilestoneCompletionRequest(@NotNull Boolean completed) {
}

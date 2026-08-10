package com.cenergy.passed_backend.domain.roadmap.dto;

import jakarta.validation.constraints.NotNull;

public record MilestoneCompletionRequest(@NotNull Boolean completed) {
}

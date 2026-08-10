package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoadmapReplanPreviewResponse(
        Long roadmapId,
        UUID replanToken,
        String summary,
        int previousRemainingMinutes,
        int replannedRemainingMinutes,
        LocalDate previousEstimatedEndDate,
        LocalDate replannedEstimatedEndDate,
        List<Change> changes
) {
    public RoadmapReplanPreviewResponse {
        changes = List.copyOf(changes);
    }

    public record Change(Long milestoneId, String title, RoadmapReplanAiResponse.Action action,
                         int previousLearningOrder, Integer replannedLearningOrder,
                         int estimatedMinutes, String reason) {
    }
}

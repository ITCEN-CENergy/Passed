package com.cenergy.passed_backend.domain.roadmap.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RoadmapListResponse(List<Item> roadmaps) {
    public RoadmapListResponse {
        roadmaps = List.copyOf(roadmaps);
    }

    public record Item(Long roadmapId, String title, RoadmapStatus status,
                       int totalEstimatedMinutes, BigDecimal progressRate,
                       int jobPostingCount, int skillCount, int milestoneCount,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}

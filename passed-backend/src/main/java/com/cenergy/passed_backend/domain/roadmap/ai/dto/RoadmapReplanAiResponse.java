package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import java.util.List;

public record RoadmapReplanAiResponse(String summary, List<Decision> decisions) {
    public enum Action {KEEP, REMOVE}

    public record Decision(Long milestoneId, Action action, Integer learningOrder, String reason) {
    }
}

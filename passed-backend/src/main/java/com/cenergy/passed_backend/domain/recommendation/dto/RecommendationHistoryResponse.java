package com.cenergy.passed_backend.domain.recommendation.dto;

import java.util.List;

public record RecommendationHistoryResponse(
        List<RecommendationHistoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public RecommendationHistoryResponse {
        content = List.copyOf(content);
    }
}

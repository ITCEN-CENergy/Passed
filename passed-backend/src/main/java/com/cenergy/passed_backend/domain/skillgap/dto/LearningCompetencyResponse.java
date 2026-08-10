package com.cenergy.passed_backend.domain.skillgap.dto;

import java.util.List;

public record LearningCompetencyResponse(
        Long userId,
        Long jobPostingId,
        List<LearningCompetencyItem> competencies
) {
}

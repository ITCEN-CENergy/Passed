package com.cenergy.passed_backend.domain.skillgap.dto;

import java.util.List;

public record SkillGapResponse(
        Long userId,
        Long jobPostingId,
        List<CompetencyGapResponse> competencyGaps
) {
}

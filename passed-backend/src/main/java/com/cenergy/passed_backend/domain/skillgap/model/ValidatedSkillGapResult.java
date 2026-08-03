package com.cenergy.passed_backend.domain.skillgap.model;

import java.util.List;

public record ValidatedSkillGapResult(
        long userId,
        long jobPostingId,
        List<ValidatedCompetencyGap> competencyGaps
) {
    public ValidatedSkillGapResult {
        competencyGaps = List.copyOf(competencyGaps);
    }
}

package com.cenergy.passed_backend.domain.roadmap.skillgap.model;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;

public record CompetencyGapSource(
        Long jobPostingId,
        Long reportId,
        Long standardCompetencyId,
        String standardCompetencyName,
        CompetencyCategory category,
        int currentLevel,
        String currentEvidence,
        RequirementType requirementType,
        int targetLevel,
        int gapLevel
) {
}

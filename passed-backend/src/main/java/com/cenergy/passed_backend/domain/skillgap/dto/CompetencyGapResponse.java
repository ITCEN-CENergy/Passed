package com.cenergy.passed_backend.domain.skillgap.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;

public record CompetencyGapResponse(
        Long standardCompetencyId,
        String standardCompetencyName,
        CompetencyCategory category,
        RequirementType requirementType,
        Integer currentLevel,
        Integer targetLevel,
        Integer gapLevel,
        String currentEvidence
) {
}

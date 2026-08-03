package com.cenergy.passed_backend.domain.skillgap.model;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;

public record ValidatedCompetencyGap(
        Long standardCompetencyId,
        String standardCompetencyName,
        CompetencyCategory category,
        RequirementType requirementType,
        int currentLevel,
        int targetLevel,
        int gapLevel,
        String currentEvidence
) {
}

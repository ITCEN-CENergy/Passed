package com.cenergy.passed_backend.domain.roadmap.skillgap.model;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;

import java.util.List;

public record MergedCompetencyGap(
        String roadmapSkillKey,
        Long standardCompetencyId,
        String standardCompetencyName,
        CompetencyCategory category,
        int currentLevel,
        int targetLevel,
        RequirementType requirementType,
        int gapLevel,
        int frequency,
        int priorityScore,
        int priority,
        List<CompetencyGapSource> sources
) {
    public MergedCompetencyGap {
        sources = List.copyOf(sources);
    }
}

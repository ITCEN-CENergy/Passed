package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;

import java.util.List;

public record RoadmapAiRequest(
        Long userId,
        List<Competency> competencies
) {
    public RoadmapAiRequest {
        competencies = competencies == null ? null : List.copyOf(competencies);
    }

    public static RoadmapAiRequest from(Long userId, List<MergedCompetencyGap> competencies) {
        return new RoadmapAiRequest(
                userId,
                competencies.stream().map(Competency::from).toList()
        );
    }

    public record Competency(
            String roadmapSkillKey,
            Long standardCompetencyId,
            String standardCompetencyName,
            CompetencyCategory category,
            Integer currentLevel,
            Integer targetLevel,
            RequirementType requirementType,
            Integer gapLevel,
            Integer frequency,
            Integer priority,
            List<Source> sources
    ) {
        public Competency {
            sources = sources == null ? null : List.copyOf(sources);
        }

        private static Competency from(MergedCompetencyGap gap) {
            return new Competency(
                    gap.roadmapSkillKey(), gap.standardCompetencyId(), gap.standardCompetencyName(),
                    gap.category(), gap.currentLevel(), gap.targetLevel(), gap.requirementType(),
                    gap.gapLevel(), gap.frequency(), gap.priority(),
                    gap.sources().stream()
                            .map(source -> new Source(source.jobPostingId(), source.currentEvidence()))
                            .toList()
            );
        }
    }

    public record Source(Long jobPostingId, String currentEvidence) {
    }
}

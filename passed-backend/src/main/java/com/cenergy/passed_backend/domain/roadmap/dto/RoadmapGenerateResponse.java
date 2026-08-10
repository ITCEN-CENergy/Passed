package com.cenergy.passed_backend.domain.roadmap.dto;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapGenerationResult;
import com.cenergy.passed_backend.domain.roadmap.entity.*;

import java.util.List;

public record RoadmapGenerateResponse(Long roadmapId, String title, List<Skill> skills) {
    public RoadmapGenerateResponse {
        skills = List.copyOf(skills);
    }

    public RoadmapGenerateResponse(String title, List<Skill> skills) {
        this(null, title, skills);
    }

    public static RoadmapGenerateResponse from(Long roadmapId, RoadmapGenerationResult result) {
        return new RoadmapGenerateResponse(roadmapId, result.title(),
                result.skills().stream().map(Skill::from).toList());
    }

    public record Skill(
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
            List<Milestone> milestones
    ) {
        public Skill {
            milestones = List.copyOf(milestones);
        }

        private static Skill from(RoadmapGenerationResult.Skill value) {
            return new Skill(value.roadmapSkillKey(), value.standardCompetencyId(),
                    value.standardCompetencyName(), value.category(), value.currentLevel(), value.targetLevel(),
                    value.requirementType(), value.gapLevel(), value.frequency(), value.priorityScore(),
                    value.priority(), value.milestones().stream().map(Milestone::from).toList());
        }
    }

    public record Milestone(
            String title,
            String description,
            String learningObjective,
            String completionCriteria,
            int startLevel,
            int targetLevel,
            MilestoneType milestoneType,
            Difficulty difficulty,
            int estimatedMinutes,
            int learningOrder,
            List<LearningResource> learningResources
    ) {
        private static Milestone from(RoadmapGenerationResult.Milestone value) {
            return new Milestone(value.title(), value.description(), value.learningObjective(),
                    value.completionCriteria(), value.startLevel(), value.targetLevel(), value.milestoneType(),
                    value.difficulty(), value.estimatedMinutes(), value.learningOrder(),
                    value.learningResources().stream().map(LearningResource::from).toList());
        }
    }

    public record LearningResource(String resourceId, String resourceType, String title,
                                   String description, String provider, String url,
                                   String thumbnailUrl, List<String> authors,
                                   Boolean isFree) {
        private static LearningResource from(RoadmapGenerationResult.LearningResource value) {
            return new LearningResource(value.resourceId(), value.resourceType(), value.title(),
                    value.description(), value.provider(), value.url(), value.thumbnailUrl(),
                    value.authors(), value.isFree());
        }
    }
}

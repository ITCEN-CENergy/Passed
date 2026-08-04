package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapMilestone;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapSkill;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;
import com.cenergy.passed_backend.global.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RoadmapGenerationResult(String title, List<Skill> skills) {
    public RoadmapGenerationResult {
        skills = List.copyOf(skills);
    }

    public static RoadmapGenerationResult combine(List<MergedCompetencyGap> gaps,
                                                  ValidatedRoadmapAiResult aiResult) {
        Map<String, ValidatedRoadmapSkill> aiByKey = new LinkedHashMap<>();
        for (ValidatedRoadmapSkill aiSkill : aiResult.skills()) {
            if (aiByKey.putIfAbsent(aiSkill.roadmapSkillKey(), aiSkill) != null) {
                throw new RoadmapException(ErrorCode.ROADMAP_GENERATION_FAILED,
                        "Duplicate roadmapSkillKey in generated result");
            }
        }
        List<Skill> skills = gaps.stream().map(gap -> {
            ValidatedRoadmapSkill aiSkill = aiByKey.remove(gap.roadmapSkillKey());
            if (aiSkill == null) {
                throw new RoadmapException(ErrorCode.ROADMAP_GENERATION_FAILED,
                        "Missing roadmapSkillKey in generated result");
            }
            return Skill.from(gap, aiSkill);
        }).toList();
        if (!aiByKey.isEmpty()) {
            throw new RoadmapException(ErrorCode.ROADMAP_GENERATION_FAILED,
                    "Unexpected roadmapSkillKey in generated result");
        }
        return new RoadmapGenerationResult(aiResult.title(), skills);
    }

    public record Skill(String roadmapSkillKey, Long standardCompetencyId, String standardCompetencyName,
                        CompetencyCategory category, int currentLevel, int targetLevel,
                        RequirementType requirementType, int gapLevel, int frequency,
                        int priorityScore, int priority, List<Milestone> milestones) {
        public Skill {
            milestones = List.copyOf(milestones);
        }

        private static Skill from(MergedCompetencyGap gap, ValidatedRoadmapSkill aiSkill) {
            return new Skill(gap.roadmapSkillKey(), gap.standardCompetencyId(), gap.standardCompetencyName(),
                    gap.category(), gap.currentLevel(), gap.targetLevel(), gap.requirementType(), gap.gapLevel(),
                    gap.frequency(), gap.priorityScore(), gap.priority(),
                    aiSkill.milestones().stream().map(Milestone::from).toList());
        }
    }

    public record Milestone(String title, String description, String learningObjective,
                            String completionCriteria, int startLevel, int targetLevel,
                            MilestoneType milestoneType, Difficulty difficulty,
                            int estimatedMinutes, int learningOrder) {
        private static Milestone from(ValidatedRoadmapMilestone value) {
            return new Milestone(value.title(), value.description(), value.learningObjective(),
                    value.completionCriteria(), value.startLevel(), value.targetLevel(), value.milestoneType(),
                    value.difficulty(), value.estimatedMinutes(), value.learningOrder());
        }
    }
}

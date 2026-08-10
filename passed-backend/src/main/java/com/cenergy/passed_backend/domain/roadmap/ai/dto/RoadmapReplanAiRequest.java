package com.cenergy.passed_backend.domain.roadmap.ai.dto;

import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;

import java.util.List;

public record RoadmapReplanAiRequest(
        Long roadmapId,
        String title,
        String userInstruction,
        List<Group> groups
) {
    public RoadmapReplanAiRequest { groups = List.copyOf(groups); }

    public record Group(String groupKey, Long roadmapSkillId, Long standardCompetencyId, String skillName,
                        CompetencyCategory category, int currentLevel, int targetLevel,
                        int assignedEstimatedMinutes, List<SourceMilestone> sourceMilestones) {
        public Group { sourceMilestones = List.copyOf(sourceMilestones); }
    }

    public record SourceMilestone(String title, String description, String learningObjective,
                                  String completionCriteria, int startLevel, int targetLevel,
                                  MilestoneType milestoneType, Difficulty difficulty,
                                  int estimatedMinutes) {
    }
}

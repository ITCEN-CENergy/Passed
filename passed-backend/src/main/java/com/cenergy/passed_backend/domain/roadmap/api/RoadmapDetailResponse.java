package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RoadmapDetailResponse(
        Long roadmapId, String title, RoadmapStatus status, int totalEstimatedMinutes,
        BigDecimal progressRate, LocalDate baselineEndDate, LocalDate estimatedEndDate,
        RoadmapScheduleStatus scheduleStatus, long delayDays, boolean replanRecommended,
        String failureReason,
        List<Long> jobPostingIds, List<Skill> skills,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public RoadmapDetailResponse {
        jobPostingIds = List.copyOf(jobPostingIds);
        skills = List.copyOf(skills);
    }

    public record Skill(Long roadmapSkillId, Long standardCompetencyId, String standardCompetencyName,
                        CompetencyCategory category, int currentLevel, int targetLevel,
                        RequirementType requirementType, int gapLevel, int frequency,
                        BigDecimal priorityScore, int priority, int estimatedMinutes,
                        BigDecimal progressRate, List<Source> sources, List<MilestoneItem> milestones) {
        public Skill {
            sources = List.copyOf(sources);
            milestones = List.copyOf(milestones);
        }
    }

    public record Source(Long jobPostingId, Long reportId, int currentLevel,
                         String currentEvidence, RequirementType requirementType,
                         int targetLevel, int gapLevel) {
    }

    public record MilestoneItem(Long milestoneId, String title, String description,
                                String learningObjective, String completionCriteria,
                                int startLevel, int targetLevel, MilestoneType milestoneType,
                                Difficulty difficulty, int estimatedMinutes, int learningOrder,
                                MilestoneStatus status, BigDecimal progressRate, ReuseType reuseType,
                                String reuseReason, boolean required,
                                List<Resource> learningResources) {
        public MilestoneItem {
            learningResources = List.copyOf(learningResources);
        }
    }

    public record Resource(Long resourceId, String externalId, String resourceType,
                           String title, String description, String provider, String url,
                           String thumbnailUrl, int rankOrder) {
    }
}

package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;

import java.util.List;
import java.util.Objects;
import java.time.OffsetDateTime;

public record RecommendationRunContext(
        Long recommendationRunId,
        RecommendationScoringPolicy policy,
        List<RecommendationGradeRule> gradeRules,
        List<UserSkillData> userSkills,
        int importantSkillCount,
        String userSkillSnapshotHash,
        Long industryId,
        List<Long> jobRoleIds,
        OffsetDateTime startedAt
) {
    public RecommendationRunContext {
        Objects.requireNonNull(recommendationRunId, "recommendationRunId must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        gradeRules = List.copyOf(Objects.requireNonNull(gradeRules, "gradeRules must not be null"));
        userSkills = List.copyOf(Objects.requireNonNull(userSkills, "userSkills must not be null"));
        Objects.requireNonNull(userSkillSnapshotHash, "userSkillSnapshotHash must not be null");
        Objects.requireNonNull(industryId, "industryId must not be null");
        jobRoleIds = List.copyOf(Objects.requireNonNull(jobRoleIds, "jobRoleIds must not be null"));
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }
}

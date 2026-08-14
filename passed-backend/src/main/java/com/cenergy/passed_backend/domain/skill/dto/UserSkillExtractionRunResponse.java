package com.cenergy.passed_backend.domain.skill.dto;

import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRun;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRunStatus;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionStage;

import java.time.OffsetDateTime;

public record UserSkillExtractionRunResponse(
        Long extractionId,
        UserSkillExtractionRunStatus status,
        UserSkillExtractionStage stage,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
    public static UserSkillExtractionRunResponse from(UserSkillExtractionRun run) {
        return new UserSkillExtractionRunResponse(
                run.getId(), run.getStatus(), run.getStage(), run.getFailureMessage(),
                run.getCreatedAt(), run.getCompletedAt()
        );
    }
}

package com.cenergy.passed_backend.domain.skill.dto;

import com.cenergy.passed_backend.domain.skill.ai.dto.UserSkillAiResponse;

public record UserSkillExtractionResponse(
        int processedChunkCount,
        int skillCount,
        int unmappedCount,
        boolean persisted,
        int resumeChunksEmbedded,
        int coverLetterChunksEmbedded
) {
    public static UserSkillExtractionResponse from(UserSkillAiResponse response) {
        return new UserSkillExtractionResponse(
                response.processedChunkCount(),
                response.skillCount(),
                response.unmappedCount(),
                response.persisted(),
                response.resumeChunksEmbedded(),
                response.coverLetterChunksEmbedded()
        );
    }
}

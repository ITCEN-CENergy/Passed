package com.cenergy.passed_backend.domain.skill.ai.dto;

public record UserSkillAiResponse(
        Long userId,
        Integer processedChunkCount,
        Integer skillCount,
        Integer unmappedCount,
        Boolean persisted,
        Integer resumeChunksEmbedded,
        Integer coverLetterChunksEmbedded
) {
}

package com.cenergy.passed_backend.domain.skill.dto;

import com.cenergy.passed_backend.domain.skill.entity.SkillMappingMethod;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillEvidence;

import java.math.BigDecimal;

public record UserSkillEvidenceResponse(
        Long evidenceId,
        String sourceType,
        Long sourceChunkId,
        String extractedName,
        String evidenceText,
        short extractedLevel,
        SkillMappingMethod mappingMethod,
        BigDecimal mappingSimilarity,
        BigDecimal mappingConfidence
) {
    public static UserSkillEvidenceResponse from(UserSkillEvidence evidence) {
        boolean resumeSource = evidence.getResumeChunk() != null;
        return new UserSkillEvidenceResponse(
                evidence.getId(),
                resumeSource ? "RESUME" : "COVER_LETTER",
                resumeSource
                        ? evidence.getResumeChunk().getId()
                        : evidence.getCoverLetterChunk().getId(),
                evidence.getExtractedName(),
                evidence.getEvidenceText(),
                evidence.getExtractedLevel(),
                evidence.getMappingMethod(),
                evidence.getMappingSimilarity(),
                evidence.getMappingConfidence()
        );
    }
}

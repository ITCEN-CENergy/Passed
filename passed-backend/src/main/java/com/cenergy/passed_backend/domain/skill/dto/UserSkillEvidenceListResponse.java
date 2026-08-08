package com.cenergy.passed_backend.domain.skill.dto;

import java.util.List;

public record UserSkillEvidenceListResponse(
        Long userSkillId,
        List<UserSkillEvidenceResponse> evidences
) {
}

package com.cenergy.passed_backend.domain.skill.ai.client;

import com.cenergy.passed_backend.domain.skill.ai.dto.UserSkillAiResponse;

public interface UserSkillAiClient {
    UserSkillAiResponse extract(Long userId);
}

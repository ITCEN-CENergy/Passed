package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.ai.client.UserSkillAiClient;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillExtractionResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class UserSkillExtractionService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserSkillAiClient userSkillAiClient;

    public UserSkillExtractionService(
            CurrentUserIdProvider currentUserIdProvider,
            UserSkillAiClient userSkillAiClient
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userSkillAiClient = userSkillAiClient;
    }

    public UserSkillExtractionResponse extract() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new UserSkillException(
                    ErrorCode.USER_SKILL_INVALID_REQUEST,
                    "Current user is required"
            );
        }
        return UserSkillExtractionResponse.from(userSkillAiClient.extract(userId));
    }
}

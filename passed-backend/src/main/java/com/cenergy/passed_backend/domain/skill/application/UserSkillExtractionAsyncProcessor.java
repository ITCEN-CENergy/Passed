package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.domain.skill.ai.client.UserSkillAiClient;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionStage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserSkillExtractionAsyncProcessor {
    private final UserSkillAiClient aiClient;
    private final UserSkillExtractionRunStateService stateService;

    public UserSkillExtractionAsyncProcessor(
            UserSkillAiClient aiClient,
            UserSkillExtractionRunStateService stateService
    ) {
        this.aiClient = aiClient;
        this.stateService = stateService;
    }

    @Async("onboardingTaskExecutor")
    public void process(Long runId, Long userId) {
        try {
            stateService.moveTo(runId, UserSkillExtractionStage.SKILL_EXTRACTION);
            aiClient.extract(userId);
            stateService.moveTo(runId, UserSkillExtractionStage.COMPETENCY_ORGANIZATION);
            stateService.complete(runId);
        } catch (RuntimeException exception) {
            stateService.fail(runId, exception);
        }
    }
}

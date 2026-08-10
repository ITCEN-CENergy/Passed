package com.cenergy.passed_backend.domain.skillgap.application;

import com.cenergy.passed_backend.domain.skillgap.ai.client.LearningCompetencyAiClient;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class AiLearningCompetencyService implements LearningCompetencyService {
    private final LearningCompetencyAiClient aiClient;

    public AiLearningCompetencyService(LearningCompetencyAiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId) {
        return aiClient.getLearningCompetencies(jobPostingId, userId);
    }
}

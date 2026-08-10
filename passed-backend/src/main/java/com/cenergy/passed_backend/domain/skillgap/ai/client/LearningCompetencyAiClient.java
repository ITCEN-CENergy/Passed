package com.cenergy.passed_backend.domain.skillgap.ai.client;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;

public interface LearningCompetencyAiClient {
    LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId);
}

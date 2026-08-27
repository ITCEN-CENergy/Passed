package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.VerifiedSkillMatch;

import java.util.List;

public interface RecommendationSkillVerificationClient {
    List<VerifiedSkillMatch> verify(Long userId, List<Long> targetSkillIds);
}

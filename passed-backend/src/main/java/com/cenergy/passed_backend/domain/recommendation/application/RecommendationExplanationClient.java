package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanationInput;

import java.util.List;

public interface RecommendationExplanationClient {
    List<RecommendationExplanation> generate(List<RecommendationExplanationInput> inputs);
}

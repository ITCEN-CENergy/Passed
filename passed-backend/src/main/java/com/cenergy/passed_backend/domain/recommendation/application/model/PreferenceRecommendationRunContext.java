package com.cenergy.passed_backend.domain.recommendation.application.model;

import java.util.List;
import java.util.Objects;

public record PreferenceRecommendationRunContext (
        RecommendationRunContext run,
        Long industryId,
        List<Long> jobRoleIds
){
    public PreferenceRecommendationRunContext {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(industryId, "industryId must not be null");
        jobRoleIds = List.copyOf(Objects.requireNonNull(jobRoleIds, "jobRoleIds must not be null"));
    }

}

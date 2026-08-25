package com.cenergy.passed_backend.domain.recommendation.application.model;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RecommendationCandidateSelectionResult(
        Map<Long, PostingSkillBundle> candidates,
        Map<Long, RequiredSkillEvaluation> requiredQualifiedCandidates,
        List<UserSkillData> effectiveUserSkills
) {
    public RecommendationCandidateSelectionResult {
        candidates = immutableCopy(candidates, "candidates");
        requiredQualifiedCandidates = immutableCopy(
                requiredQualifiedCandidates,
                "requiredQualifiedCandidates"
        );
        if (!candidates.keySet().containsAll(requiredQualifiedCandidates.keySet())) {
            throw new IllegalArgumentException("qualified candidates must be included in candidates");
        }
        effectiveUserSkills = List.copyOf(Objects.requireNonNull(
                effectiveUserSkills,
                "effectiveUserSkills must not be null"
        ));
    }

    public RecommendationCandidateSelectionResult(
            Map<Long, PostingSkillBundle> candidates,
            Map<Long, RequiredSkillEvaluation> requiredQualifiedCandidates
    ) {
        this(candidates, requiredQualifiedCandidates, List.of());
    }

    public int candidatePostingCount() {
        return candidates.size();
    }

    public int requiredQualifiedPostingCount() {
        return requiredQualifiedCandidates.size();
    }

    private static <T> Map<Long, T> immutableCopy(Map<Long, T> source, String fieldName) {
        Objects.requireNonNull(source, fieldName + " must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}

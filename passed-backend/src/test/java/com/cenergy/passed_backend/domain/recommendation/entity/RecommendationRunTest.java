package com.cenergy.passed_backend.domain.recommendation.entity;

import com.cenergy.passed_backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RecommendationRunTest {

    @Test
    void recordsTheRecommendationPurposeWhenStartingARun() {
        User user = mock(User.class);
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        String snapshotHash = "a".repeat(64);
        Map<String, Object> userSkills = Map.of("skills", Map.of());

        RecommendationRun multiple = RecommendationRun.startForPreferenceRecommendation(
                user,
                policy,
                snapshotHash,
                userSkills,
                Map.of("industryId", 8L)
        );
        RecommendationRun single = RecommendationRun.startForSinglePostingRecommendation(
                user,
                policy,
                snapshotHash,
                userSkills
        );

        assertEquals(RecommendationRunType.MULTIPLE_POSTINGS, multiple.getRecommendationType());
        assertEquals(RecommendationRunType.SINGLE_POSTING, single.getRecommendationType());
    }
}

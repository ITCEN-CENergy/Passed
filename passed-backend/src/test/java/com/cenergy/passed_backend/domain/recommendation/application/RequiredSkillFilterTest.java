package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiredSkillFilterTest {
    private RequiredSkillFilter filter;
    private RecommendationScoringPolicy policy;

    @BeforeEach
    void setUp() {
        filter = new RequiredSkillFilter();
        policy = mock(RecommendationScoringPolicy.class);
        when(policy.getRequiredCoverageThreshold()).thenReturn(new BigDecimal("0.5000"));
        when(policy.getRequiredMaxScore()).thenReturn(new BigDecimal("60.00"));
    }

    @Test
    void keepsOnlyPostingsMeetingRequiredCoverageThreshold() {
        Map<Long, PostingSkillBundle> candidates = Map.of(
                100L,
                bundle(
                        List.of(skill(12L, 3), skill(999L, 2)),
                        List.of(skill(888L, 3)),
                        List.of(skill(777L, 1))
                ),
                200L,
                bundle(
                        List.of(skill(999L, 1), skill(888L, 1), skill(777L, 1)),
                        List.of(),
                        List.of()
                )
        );
        List<UserSkillData> userSkills = List.of(
                new UserSkillData(12L, (short) 3, true),
                new UserSkillData(13L, (short) 2, false)
        );

        Map<Long, RequiredSkillEvaluation> result = filter.filter(candidates, userSkills, policy);

        assertEquals(1, result.size());
        assertTrue(result.containsKey(100L));
        assertFalse(result.containsKey(200L));

        RequiredSkillEvaluation evaluation = result.get(100L);
        assertEquals(2, evaluation.requiredSkillCount());
        assertEquals(1, evaluation.requiredOwnedCount());
        assertEquals(new BigDecimal("0.5000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("0.5000"), evaluation.requiredLevelMatchRate());
        assertEquals(new BigDecimal("30.0000"), evaluation.requiredScore());
        assertEquals(2, evaluation.skillMatches().size());
        assertTrue(evaluation.skillMatches().getFirst().owned());
        assertFalse(evaluation.skillMatches().getLast().owned());
    }

    @Test
    void passesPostingWithoutRequiredSkillsUsingExplicitDefaultRates() {
        Map<Long, RequiredSkillEvaluation> result = filter.filter(
                Map.of(100L, PostingSkillBundle.empty()),
                List.of(),
                policy
        );

        RequiredSkillEvaluation evaluation = result.get(100L);
        assertEquals(0, evaluation.requiredSkillCount());
        assertEquals(0, evaluation.requiredOwnedCount());
        assertEquals(new BigDecimal("1.0000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("1.0000"), evaluation.requiredLevelMatchRate());
        assertEquals(new BigDecimal("0.0000"), evaluation.requiredScore());
        assertTrue(evaluation.skillMatches().isEmpty());
    }

    @Test
    void calculatesLevelMatchRateFromMatchingSkillIdsAndLevels() {
        Map<Long, PostingSkillBundle> candidates = Map.of(
                100L,
                bundle(
                        List.of(skill(12L, 3), skill(13L, 2)),
                        List.of(),
                        List.of()
                )
        );
        List<UserSkillData> userSkills = List.of(
                new UserSkillData(12L, (short) 2, true),
                new UserSkillData(13L, (short) 3, false)
        );

        RequiredSkillEvaluation evaluation = filter.filter(candidates, userSkills, policy).get(100L);

        assertEquals(new BigDecimal("1.0000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("0.8333"), evaluation.requiredLevelMatchRate());
        assertEquals(new BigDecimal("49.9980"), evaluation.requiredScore());
        assertFalse(evaluation.skillMatches().getFirst().requirementSatisfied());
        assertTrue(evaluation.skillMatches().getLast().requirementSatisfied());
    }

    private PostingSkillBundle bundle(
            List<PostingSkillBundle.PostingSkill> required,
            List<PostingSkillBundle.PostingSkill> preferred,
            List<PostingSkillBundle.PostingSkill> related
    ) {
        return new PostingSkillBundle(required, preferred, related);
    }

    private PostingSkillBundle.PostingSkill skill(Long skillId, int requiredLevel) {
        return new PostingSkillBundle.PostingSkill(skillId, (short) requiredLevel);
    }
}

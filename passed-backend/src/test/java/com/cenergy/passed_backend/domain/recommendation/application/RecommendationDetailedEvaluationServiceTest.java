package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationDetailedEvaluationServiceTest {
    @Test
    void calculatesAllSkillAreasImportantBonusAndPrimaryTier() {
        RecommendationScoringPolicy policy = policy();
        List<UserSkillData> userSkills = List.of(
                new UserSkillData(1L, (short) 3, true),
                new UserSkillData(2L, (short) 1, false),
                new UserSkillData(3L, (short) 1, false)
        );
        PostingSkillBundle bundle = new PostingSkillBundle(
                List.of(skill(1L, "Java", SkillCategory.TECHNICAL_SKILL, 3)),
                List.of(skill(2L, "정보처리기사", SkillCategory.CERTIFICATION, 3)),
                List.of(skill(3L, "API", SkillCategory.TECHNICAL_SKILL, 2))
        );
        RequiredSkillFilter requiredFilter = new RequiredSkillFilter();
        Map<Long, PostingSkillBundle> candidates = Map.of(100L, bundle);
        Map<Long, RequiredSkillEvaluation> qualified = requiredFilter.filter(
                candidates,
                userSkills,
                policy
        );

        RecommendationScoreResult result = new RecommendationDetailedEvaluationService()
                .evaluate(
                        new RecommendationCandidateSelectionResult(candidates, qualified),
                        userSkills,
                        policy
                )
                .getFirst();

        assertEquals(new BigDecimal("60.0000"), result.requiredScore());
        assertEquals(new BigDecimal("20.0000"), result.preferredScore());
        assertEquals(new BigDecimal("5.0000"), result.relatedScore());
        assertEquals(new BigDecimal("10.0000"), result.importantSkillBonus());
        assertEquals(new BigDecimal("95.0000"), result.totalScore());
        assertEquals(1, result.importantSkillCount());
        assertEquals(1, result.importantMatchCount());
        assertEquals(RecommendationCandidateTier.PRIMARY, result.candidateTier());
        EvaluatedSkillDetail certification = result.skillDetails().stream()
                .filter(value -> value.skillId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertEquals(SkillEvaluationType.OWNERSHIP, certification.evaluationType());
        assertEquals(new BigDecimal("1.0000"), certification.matchRate());
        assertTrue(certification.requirementSatisfied());
    }

    @Test
    void assignsFallbackAndZeroBonusWhenUserHasNoImportantSkills() {
        RecommendationScoringPolicy policy = policy();
        List<UserSkillData> userSkills = List.of(new UserSkillData(1L, (short) 3, false));
        PostingSkillBundle bundle = new PostingSkillBundle(
                List.of(skill(1L, "Java", SkillCategory.TECHNICAL_SKILL, 3)),
                List.of(),
                List.of()
        );
        Map<Long, PostingSkillBundle> candidates = Map.of(100L, bundle);
        Map<Long, RequiredSkillEvaluation> qualified = new RequiredSkillFilter().filter(
                candidates,
                userSkills,
                policy
        );

        RecommendationScoreResult result = new RecommendationDetailedEvaluationService()
                .evaluate(
                        new RecommendationCandidateSelectionResult(candidates, qualified),
                        userSkills,
                        policy
                )
                .getFirst();

        assertEquals(new BigDecimal("0.0000"), result.importantSkillBonus());
        assertEquals(RecommendationCandidateTier.FALLBACK, result.candidateTier());
    }

    private RecommendationScoringPolicy policy() {
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        when(policy.getRequiredMaxScore()).thenReturn(new BigDecimal("60.00"));
        when(policy.getPreferredMaxScore()).thenReturn(new BigDecimal("20.00"));
        when(policy.getRelatedMaxScore()).thenReturn(new BigDecimal("10.00"));
        when(policy.getImportantBonusMaxScore()).thenReturn(new BigDecimal("10.00"));
        when(policy.getRequiredCoverageThreshold()).thenReturn(new BigDecimal("0.5000"));
        when(policy.getPrimaryImportantMatchCount()).thenReturn(1);
        when(policy.getImportantRequiredWeight()).thenReturn(new BigDecimal("1.0000"));
        when(policy.getImportantPreferredWeight()).thenReturn(new BigDecimal("0.7000"));
        when(policy.getImportantRelatedWeight()).thenReturn(new BigDecimal("0.4000"));
        return policy;
    }

    private PostingSkillBundle.PostingSkill skill(
            Long id,
            String name,
            SkillCategory category,
            int requiredLevel
    ) {
        return new PostingSkillBundle.PostingSkill(id, name, category, (short) requiredLevel);
    }
}

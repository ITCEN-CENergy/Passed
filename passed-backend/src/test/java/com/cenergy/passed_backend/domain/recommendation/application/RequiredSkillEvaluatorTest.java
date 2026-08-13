package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredSkillEvaluatorTest {
    private RequiredSkillEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RequiredSkillEvaluator();
    }

    @Test
    void evaluatesEveryPostingWithoutFiltering() {
        Map<Long, PostingSkillBundle> candidates = Map.of(
                100L, bundle(List.of(skill(12L, 3), skill(999L, 2))),
                200L, bundle(List.of(skill(999L, 1), skill(888L, 1), skill(777L, 1)))
        );
        List<UserSkillData> userSkills = List.of(
                new UserSkillData(12L, (short) 3, true),
                new UserSkillData(13L, (short) 2, false)
        );

        Map<Long, RequiredSkillEvaluation> result = evaluator.evaluateAll(candidates, userSkills);

        assertEquals(2, result.size());
        RequiredSkillEvaluation evaluation = result.get(100L);
        assertEquals(2, evaluation.requiredSkillCount());
        assertEquals(1, evaluation.requiredOwnedCount());
        assertEquals(new BigDecimal("0.5000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("0.5000"), evaluation.requiredLevelMatchRate());
        assertEquals(2, evaluation.skillMatches().size());
        assertTrue(evaluation.skillMatches().getFirst().owned());
        assertFalse(evaluation.skillMatches().getLast().owned());
    }

    @Test
    void evaluatesSinglePostingWithoutRequiredSkillsUsingDefaultRates() {
        RequiredSkillEvaluation evaluation = evaluator.evaluate(
                PostingSkillBundle.empty(),
                List.of()
        );

        assertEquals(0, evaluation.requiredSkillCount());
        assertEquals(0, evaluation.requiredOwnedCount());
        assertEquals(new BigDecimal("1.0000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("1.0000"), evaluation.requiredLevelMatchRate());
        assertTrue(evaluation.skillMatches().isEmpty());
    }

    @Test
    void calculatesLevelMatchRateFromMatchingSkillIdsAndLevels() {
        PostingSkillBundle bundle = bundle(List.of(skill(12L, 3), skill(13L, 2)));
        List<UserSkillData> userSkills = List.of(
                new UserSkillData(12L, (short) 2, true),
                new UserSkillData(13L, (short) 3, false)
        );

        RequiredSkillEvaluation evaluation = evaluator.evaluate(bundle, userSkills);

        assertEquals(new BigDecimal("1.0000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("0.8333"), evaluation.requiredLevelMatchRate());
        assertFalse(evaluation.skillMatches().getFirst().requirementSatisfied());
        assertTrue(evaluation.skillMatches().getLast().requirementSatisfied());
    }

    @Test
    void evaluatesCertificationByOwnershipRegardlessOfRequiredLevel() {
        PostingSkillBundle bundle = bundle(List.of(certification(12L, 3)));

        RequiredSkillEvaluation evaluation = evaluator.evaluate(
                bundle,
                List.of(new UserSkillData(12L, (short) 1, false))
        );

        assertEquals(new BigDecimal("1.0000"), evaluation.requiredCoverageRate());
        assertEquals(new BigDecimal("1.0000"), evaluation.requiredLevelMatchRate());
        assertTrue(evaluation.skillMatches().getFirst().requirementSatisfied());
    }

    private PostingSkillBundle bundle(List<PostingSkillBundle.PostingSkill> required) {
        return new PostingSkillBundle(required, List.of(), List.of());
    }

    private PostingSkillBundle.PostingSkill skill(Long skillId, int requiredLevel) {
        return new PostingSkillBundle.PostingSkill(
                skillId,
                "skill-" + skillId,
                SkillCategory.TECHNICAL_SKILL,
                (short) requiredLevel
        );
    }

    private PostingSkillBundle.PostingSkill certification(Long skillId, int requiredLevel) {
        return new PostingSkillBundle.PostingSkill(
                skillId,
                "certification-" + skillId,
                SkillCategory.CERTIFICATION,
                (short) requiredLevel
        );
    }
}

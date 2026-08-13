package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RequiredSkillEvaluator {
    private static final int RATE_SCALE = 4;
    private static final int CALCULATION_SCALE = 12;
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE);
    private static final BigDecimal FULL_RATE = BigDecimal.ONE.setScale(RATE_SCALE);

    public RequiredSkillEvaluation evaluate(
            PostingSkillBundle bundle,
            Collection<UserSkillData> userSkills
    ) {
        Objects.requireNonNull(bundle, "posting skill bundle must not be null");
        Map<Long, Short> userLevels = indexUserLevels(userSkills);

        return evaluateRequiredSkills(
                bundle.requiredSkills(),
                userLevels
        );
    }

    public Map<Long, RequiredSkillEvaluation> evaluateAll(
            Map<Long, PostingSkillBundle> candidates,
            Collection<UserSkillData> userSkills
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");

        Map<Long, Short> userLevels = indexUserLevels(userSkills);
        Map<Long, RequiredSkillEvaluation> result = new LinkedHashMap<>();

        for (Map.Entry<Long, PostingSkillBundle> entry : candidates.entrySet()) {
            Long jobPostingId = requireJobPostingId(entry.getKey());
            PostingSkillBundle bundle = Objects.requireNonNull(
                    entry.getValue(),
                    "posting skill bundle must not be null"
            );

            RequiredSkillEvaluation evaluation = evaluateRequiredSkills(
                    bundle.requiredSkills(),
                    userLevels
            );

            result.put(jobPostingId, evaluation);
        }

        return Collections.unmodifiableMap(result);
    }

    private RequiredSkillEvaluation evaluateRequiredSkills(
            List<PostingSkillBundle.PostingSkill> requiredSkills,
            Map<Long, Short> userLevels
    ) {
        Objects.requireNonNull(requiredSkills, "requiredSkills must not be null");
        Objects.requireNonNull(userLevels, "userLevels must not be null");

        int requiredSkillCount = requiredSkills.size();
        if (requiredSkillCount == 0) {
            return new RequiredSkillEvaluation(
                    0,
                    0,
                    FULL_RATE,
                    FULL_RATE,
                    List.of()
            );
        }

        int requiredOwnedCount = 0;
        BigDecimal levelMatchSum = BigDecimal.ZERO;
        List<RequiredSkillEvaluation.RequiredSkillMatch> skillMatches = new java.util.ArrayList<>();

        for (PostingSkillBundle.PostingSkill requiredSkill : requiredSkills) {
            Objects.requireNonNull(requiredSkill, "required skill must not be null");
            Short userLevel = userLevels.get(requiredSkill.skillId());
            boolean owned = userLevel != null;
            SkillEvaluationType evaluationType = evaluationType(requiredSkill.skillCategory());
            BigDecimal matchRate = matchRate(
                    evaluationType,
                    userLevel,
                    requiredSkill.requiredLevel()
            );
            boolean requirementSatisfied = owned && matchRate.compareTo(BigDecimal.ONE) >= 0;
            if (owned) {
                requiredOwnedCount++;
            }
            levelMatchSum = levelMatchSum.add(matchRate);
            skillMatches.add(new RequiredSkillEvaluation.RequiredSkillMatch(
                    requiredSkill.skillId(),
                    requiredSkill.requiredLevel(),
                    userLevel,
                    evaluationType,
                    owned,
                    requirementSatisfied,
                    matchRate.setScale(RATE_SCALE, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal requiredCoverageRate = divide(
                requiredOwnedCount,
                requiredSkillCount,
                RATE_SCALE
        );
        BigDecimal requiredLevelMatchRate = levelMatchSum.divide(
                BigDecimal.valueOf(requiredSkillCount),
                RATE_SCALE,
                RoundingMode.HALF_UP
        );

        return new RequiredSkillEvaluation(
                requiredSkillCount,
                requiredOwnedCount,
                requiredCoverageRate,
                requiredLevelMatchRate,
                skillMatches
        );
    }

    private Map<Long, Short> indexUserLevels(Collection<UserSkillData> userSkills) {
        if (userSkills == null) {
            throw new IllegalArgumentException("userSkills must not be null");
        }
        Map<Long, Short> result = new LinkedHashMap<>();
        for (UserSkillData userSkill : userSkills) {
            if (userSkill == null || userSkill.skillId() == null || userSkill.skillId() <= 0) {
                throw new IllegalArgumentException("userSkills contains an invalid skillId");
            }
            if (userSkill.skillLevel() < 1 || userSkill.skillLevel() > 3) {
                throw new IllegalArgumentException("userSkills contains an invalid skillLevel");
            }
            if (result.putIfAbsent(userSkill.skillId(), userSkill.skillLevel()) != null) {
                throw new IllegalArgumentException("userSkills contains a duplicated skillId");
            }
        }
        return Map.copyOf(result);
    }

    private SkillEvaluationType evaluationType(SkillCategory category) {
        return category == SkillCategory.CERTIFICATION
                ? SkillEvaluationType.OWNERSHIP
                : SkillEvaluationType.LEVEL;
    }

    private BigDecimal matchRate(
            SkillEvaluationType evaluationType,
            Short userLevel,
            short requiredLevel
    ) {
        if (userLevel == null) {
            return ZERO_RATE;
        }
        if (evaluationType == SkillEvaluationType.OWNERSHIP) {
            return FULL_RATE;
        }
        return levelMatchRate(userLevel, requiredLevel);
    }

    private BigDecimal levelMatchRate(short userLevel, short requiredLevel) {
        BigDecimal rate = BigDecimal.valueOf(userLevel).divide(
                BigDecimal.valueOf(requiredLevel),
                CALCULATION_SCALE,
                RoundingMode.HALF_UP
        );
        return rate.min(BigDecimal.ONE);
    }

    private BigDecimal divide(int numerator, int denominator, int scale) {
        return BigDecimal.valueOf(numerator).divide(
                BigDecimal.valueOf(denominator),
                scale,
                RoundingMode.HALF_UP
        );
    }

    private Long requireJobPostingId(Long jobPostingId) {
        if (jobPostingId == null || jobPostingId <= 0) {
            throw new IllegalArgumentException("jobPostingId must be positive");
        }
        return jobPostingId;
    }
}

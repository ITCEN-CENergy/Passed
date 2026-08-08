package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.RequiredSkillEvaluation;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
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
public class RequiredSkillFilter {
    private static final int RATE_SCALE = 4;
    private static final int CALCULATION_SCALE = 12;
    private static final int SCORE_SCALE = 4;
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE);
    private static final BigDecimal FULL_RATE = BigDecimal.ONE.setScale(RATE_SCALE);
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(SCORE_SCALE);

    public Map<Long, RequiredSkillEvaluation> filter(
            Map<Long, PostingSkillBundle> candidates,
            Collection<UserSkillData> userSkills,
            RecommendationScoringPolicy policy
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        // 사용자 스킬을 skillId -> skillLevel 형태의 Map으로 변환하여 빠른 조회가 가능하도록 인덱싱
        Map<Long, Short> userLevels = indexUserLevels(userSkills);
        // 추천정책의 자격요건 보유율 기준 조회
        BigDecimal coverageThreshold = requireRate(
                policy,
                policy == null ? null : policy.getRequiredCoverageThreshold(),
                "requiredCoverageThreshold"
        );
        // 추천정책의 자격요건 최대 점수 기준 조회
        BigDecimal requiredMaxScore = requireScore(policy == null ? null : policy.getRequiredMaxScore());

        // 필수 자격요건 기준을 통과한 공고 ID와 평가 결과 저장
        Map<Long, RequiredSkillEvaluation> qualified = new LinkedHashMap<>();
        for (Map.Entry<Long, PostingSkillBundle> entry : candidates.entrySet()) {
            Long jobPostingId = requireJobPostingId(entry.getKey());
            PostingSkillBundle bundle = Objects.requireNonNull(
                    entry.getValue(),
                    "posting skill bundle must not be null"
            );
            // 공고의 자격요건 스킬별로 사용자 스킬 및 숙련도와 비교하며 필수 스킬 매칭 정보, 점수 계산
            RequiredSkillEvaluation evaluation = evaluate(
                    bundle.requiredSkills(),
                    userLevels,
                    requiredMaxScore
            );
            // 자격요건 보유율이 추천 정책 기준(50% 이상)에 충족하면 통과
            if (evaluation.requiredCoverageRate().compareTo(coverageThreshold) >= 0) {
                qualified.put(jobPostingId, evaluation);
            }
        }
        return Collections.unmodifiableMap(qualified);
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

    private RequiredSkillEvaluation evaluate(
            List<PostingSkillBundle.PostingSkill> requiredSkills,
            Map<Long, Short> userLevels,
            BigDecimal requiredMaxScore
    ) {
        int requiredSkillCount = requiredSkills.size();
        if (requiredSkillCount == 0) {
            return new RequiredSkillEvaluation(
                    0,
                    0,
                    FULL_RATE,
                    FULL_RATE,
                    ZERO_SCORE,
                    List.of()
            );
        }

        int requiredOwnedCount = 0;
        BigDecimal levelMatchSum = BigDecimal.ZERO;
        List<RequiredSkillEvaluation.RequiredSkillMatch> skillMatches = new java.util.ArrayList<>();

        for (PostingSkillBundle.PostingSkill requiredSkill : requiredSkills) {
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

        BigDecimal requiredCoverageRate = divide(requiredOwnedCount, requiredSkillCount, RATE_SCALE);
        BigDecimal requiredLevelMatchRate = levelMatchSum.divide(
                BigDecimal.valueOf(requiredSkillCount),
                RATE_SCALE,
                RoundingMode.HALF_UP
        );
        BigDecimal requiredScore = requiredMaxScore
                .multiply(requiredLevelMatchRate)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);

        return new RequiredSkillEvaluation(
                requiredSkillCount,
                requiredOwnedCount,
                requiredCoverageRate,
                requiredLevelMatchRate,
                requiredScore,
                skillMatches
        );
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

    private BigDecimal requireRate(
            RecommendationScoringPolicy policy,
            BigDecimal value,
            String fieldName
    ) {
        Objects.requireNonNull(policy, "policy must not be null");
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
        return value;
    }

    private BigDecimal requireScore(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("requiredMaxScore must be non-negative");
        }
        return value;
    }
}

package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.recommendation.entity.SkillEvaluationType;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecommendationDetailedEvaluationService {
    private static final int RATE_SCALE = 4;
    private static final int SCORE_SCALE = 4;
    private static final int CALCULATION_SCALE = 12;
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(SCORE_SCALE);
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE);
    private static final BigDecimal FULL_RATE = BigDecimal.ONE.setScale(RATE_SCALE);

    public List<RecommendationScoreResult> evaluate(
            RecommendationCandidateSelectionResult selection,
            Collection<UserSkillData> userSkills,
            RecommendationScoringPolicy policy
    ) {
        Objects.requireNonNull(selection, "selection must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Map<Long, UserSkillData> userSkillMap = indexUserSkills(userSkills);
        // 사용자 중요 스킬 개수 계산 후, 중요 스킬 1개당 배정할 최대 보너스 점수 계산
        int importantSkillCount = (int) userSkillMap.values().stream()
                .filter(UserSkillData::important)
                .count();
        BigDecimal importantBonusUnitScore = importantSkillCount == 0
                ? ZERO_SCORE
                : policy.getImportantBonusMaxScore().divide(
                        BigDecimal.valueOf(importantSkillCount),
                        CALCULATION_SCALE,
                        RoundingMode.DOWN
                );

        // 필수 자격요건 필터를 통과한 각 공고의 최종 평가 결과 저장
        List<RecommendationScoreResult> result = new ArrayList<>();
        for (Map.Entry<Long, RequiredSkillEvaluation> entry
                : selection.requiredQualifiedCandidates().entrySet()) {
            PostingSkillBundle bundle = Objects.requireNonNull(
                    selection.candidates().get(entry.getKey()),
                    "qualified posting skill bundle must exist"
            );
            result.add(evaluatePosting(
                    entry.getKey(),
                    bundle,
                    entry.getValue(),
                    userSkillMap,
                    importantSkillCount,
                    importantBonusUnitScore,
                    policy
            ));
        }
        return List.copyOf(result);
    }

    private RecommendationScoreResult evaluatePosting(
            Long jobPostingId,
            PostingSkillBundle bundle,
            RequiredSkillEvaluation requiredEvaluation,
            Map<Long, UserSkillData> userSkillMap,
            int importantSkillCount,
            BigDecimal importantBonusUnitScore,
            RecommendationScoringPolicy policy
    ) {
        MatchAccumulator accumulator = new MatchAccumulator();
        Map<Long, RequiredSkillEvaluation.RequiredSkillMatch> requiredMatches = indexRequiredMatches(
                requiredEvaluation
        );

        // REQUIRED / PREFERRED / RELATED별 기본 점수와 중요 스킬 가산점 계산
        evaluateSkills(
                bundle.requiredSkills(),
                JobPostingSkillType.REQUIRED,
                policy.getRequiredMaxScore(),
                policy.getImportantRequiredWeight(),
                userSkillMap,
                importantBonusUnitScore,
                requiredMatches,
                accumulator
        );
        evaluateSkills(
                bundle.preferredSkills(),
                JobPostingSkillType.PREFERRED,
                policy.getPreferredMaxScore(),
                policy.getImportantPreferredWeight(),
                userSkillMap,
                importantBonusUnitScore,
                Map.of(),
                accumulator
        );
        evaluateSkills(
                bundle.relatedSkills(),
                JobPostingSkillType.RELATED,
                policy.getRelatedMaxScore(),
                policy.getImportantRelatedWeight(),
                userSkillMap,
                importantBonusUnitScore,
                Map.of(),
                accumulator
        );

        // 사용자가 중요하게 지정한 스킬이 존재하고, 공고와 매칭된 중요 스킬 수가 정책 기준 이상이면 PRIMARY로 분류
        RecommendationCandidateTier tier = importantSkillCount > 0
                && accumulator.importantMatchCount >= policy.getPrimaryImportantMatchCount()
                ? RecommendationCandidateTier.PRIMARY
                : RecommendationCandidateTier.FALLBACK;
        // 통합 점수와 최종 공고 매칭 결과 반환
        BigDecimal totalScore = accumulator.requiredScore
                .add(accumulator.preferredScore)
                .add(accumulator.relatedScore)
                .add(accumulator.importantSkillBonus)
                .setScale(SCORE_SCALE, RoundingMode.UNNECESSARY);

        return new RecommendationScoreResult(
                jobPostingId,
                totalScore,
                accumulator.requiredScore,
                accumulator.preferredScore,
                accumulator.relatedScore,
                accumulator.importantSkillBonus,
                requiredEvaluation.requiredSkillCount(),
                requiredEvaluation.requiredOwnedCount(),
                requiredEvaluation.requiredCoverageRate(),
                requiredEvaluation.requiredLevelMatchRate(),
                importantSkillCount,
                accumulator.importantMatchCount,
                tier,
                accumulator.skillDetails
        );
    }

    private void evaluateSkills(
            List<PostingSkillBundle.PostingSkill> postingSkills,
            JobPostingSkillType skillType,
            BigDecimal typeMaxScore,
            BigDecimal importantWeight,
            Map<Long, UserSkillData> userSkillMap,
            BigDecimal importantBonusUnitScore,
            Map<Long, RequiredSkillEvaluation.RequiredSkillMatch> preparedRequiredMatches,
            MatchAccumulator accumulator
    ) {
        BigDecimal baseMaxScore = postingSkills.isEmpty()
                ? ZERO_SCORE
                : typeMaxScore.divide(
                        BigDecimal.valueOf(postingSkills.size()),
                        SCORE_SCALE,
                        RoundingMode.DOWN
                );
        for (PostingSkillBundle.PostingSkill postingSkill : postingSkills) {
            UserSkillData userSkill = userSkillMap.get(postingSkill.skillId());
            // REQUIRED는 앞선 필터 단계에서 계산한 매칭 결과를 재사용하고, PREFERRED / RELATED만 새로 사용자 스킬과 비교하여 매칭률 계산
            SkillMatch match = skillType == JobPostingSkillType.REQUIRED
                    ? preparedMatch(postingSkill, preparedRequiredMatches)
                    : evaluateMatch(postingSkill, userSkill);
            boolean userImportant = userSkill != null && userSkill.important();
            // 해당 스킬의 기본 점수 기여도 계산
            BigDecimal baseContribution = baseMaxScore
                    .multiply(match.matchRate())
                    .setScale(SCORE_SCALE, RoundingMode.DOWN);
            // 중요스킬 가산점 계산
            BigDecimal importantContribution = userImportant
                    ? importantBonusUnitScore
                            .multiply(match.matchRate())
                            .multiply(importantWeight)
                            .setScale(SCORE_SCALE, RoundingMode.DOWN)
                    : ZERO_SCORE;

            // 누적 점수와 중요 스킬 매칭 개수, 스킬별 상세 결과를 한 번에 누적
            accumulator.add(
                    skillType,
                    baseContribution,
                    importantContribution,
                    userImportant && match.owned(),
                    // 스킬별 상세 내역 저장
                    new EvaluatedSkillDetail(
                            postingSkill.skillId(),
                            postingSkill.skillName(),
                            skillType,
                            postingSkill.requiredLevel(),
                            match.userLevel(),
                            match.evaluationType(),
                            match.owned(),
                            match.requirementSatisfied(),
                            userImportant,
                            match.matchRate(),
                            baseMaxScore,
                            baseContribution,
                            importantContribution
                    )
            );
        }
    }

    private SkillMatch preparedMatch(
            PostingSkillBundle.PostingSkill postingSkill,
            Map<Long, RequiredSkillEvaluation.RequiredSkillMatch> preparedMatches
    ) {
        RequiredSkillEvaluation.RequiredSkillMatch value = preparedMatches.get(postingSkill.skillId());
        if (value == null) {
            throw new IllegalStateException("required skill evaluation is incomplete");
        }
        return new SkillMatch(
                value.userLevel(),
                value.evaluationType(),
                value.owned(),
                value.requirementSatisfied(),
                value.matchRate()
        );
    }

    private SkillMatch evaluateMatch(
            PostingSkillBundle.PostingSkill postingSkill,
            UserSkillData userSkill
    ) {
        Short userLevel = userSkill == null ? null : userSkill.skillLevel();
        boolean owned = userLevel != null;
        SkillEvaluationType evaluationType = postingSkill.skillCategory() == SkillCategory.CERTIFICATION
                ? SkillEvaluationType.OWNERSHIP
                : SkillEvaluationType.LEVEL;
        BigDecimal matchRate;
        if (!owned) {
            matchRate = ZERO_RATE;
        } else if (evaluationType == SkillEvaluationType.OWNERSHIP) {
            matchRate = FULL_RATE;
        } else {
            matchRate = BigDecimal.valueOf(userLevel)
                    .divide(
                            BigDecimal.valueOf(postingSkill.requiredLevel()),
                            RATE_SCALE,
                            RoundingMode.HALF_UP
                    )
                    .min(FULL_RATE);
        }
        return new SkillMatch(
                userLevel,
                evaluationType,
                owned,
                owned && matchRate.compareTo(BigDecimal.ONE) >= 0,
                matchRate
        );
    }

    private Map<Long, UserSkillData> indexUserSkills(Collection<UserSkillData> userSkills) {
        Objects.requireNonNull(userSkills, "userSkills must not be null");
        Map<Long, UserSkillData> result = new LinkedHashMap<>();
        for (UserSkillData userSkill : userSkills) {
            if (userSkill == null || result.putIfAbsent(userSkill.skillId(), userSkill) != null) {
                throw new IllegalArgumentException("userSkills must contain unique values");
            }
        }
        return Map.copyOf(result);
    }

    private Map<Long, RequiredSkillEvaluation.RequiredSkillMatch> indexRequiredMatches(
            RequiredSkillEvaluation evaluation
    ) {
        Map<Long, RequiredSkillEvaluation.RequiredSkillMatch> result = new LinkedHashMap<>();
        for (RequiredSkillEvaluation.RequiredSkillMatch match : evaluation.skillMatches()) {
            if (result.putIfAbsent(match.skillId(), match) != null) {
                throw new IllegalStateException("required skill evaluation contains duplicates");
            }
        }
        if (result.size() != evaluation.requiredSkillCount()) {
            throw new IllegalStateException("required skill evaluation count is inconsistent");
        }
        return Map.copyOf(result);
    }

    private record SkillMatch(
            Short userLevel,
            SkillEvaluationType evaluationType,
            boolean owned,
            boolean requirementSatisfied,
            BigDecimal matchRate
    ) {
    }

    private static final class MatchAccumulator {
        private BigDecimal requiredScore = ZERO_SCORE;
        private BigDecimal preferredScore = ZERO_SCORE;
        private BigDecimal relatedScore = ZERO_SCORE;
        private BigDecimal importantSkillBonus = ZERO_SCORE;
        private int importantMatchCount;
        private final List<EvaluatedSkillDetail> skillDetails = new ArrayList<>();

        private void add(
                JobPostingSkillType skillType,
                BigDecimal baseContribution,
                BigDecimal importantContribution,
                boolean importantMatched,
                EvaluatedSkillDetail detail
        ) {
            switch (skillType) {
                case REQUIRED -> requiredScore = requiredScore.add(baseContribution);
                case PREFERRED -> preferredScore = preferredScore.add(baseContribution);
                case RELATED -> relatedScore = relatedScore.add(baseContribution);
            }
            importantSkillBonus = importantSkillBonus.add(importantContribution);
            if (importantMatched) {
                importantMatchCount++;
            }
            skillDetails.add(detail);
        }
    }
}

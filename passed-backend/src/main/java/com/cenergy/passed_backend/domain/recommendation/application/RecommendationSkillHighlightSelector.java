package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.recommendation.application.model.EvaluatedSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class RecommendationSkillHighlightSelector {
    private static final int LIMIT = 5;

    public Selection selectEvaluated(List<EvaluatedSkillDetail> details) {
        Objects.requireNonNull(details, "details must not be null");
        return select(details.stream().map(this::fromEvaluated).toList());
    }

    public Selection selectPersisted(List<JobRecommendationSkillDetail> details) {
        Objects.requireNonNull(details, "details must not be null");
        return select(details.stream().map(this::fromPersisted).toList());
    }

    private Selection select(List<SkillFact> facts) {
        List<SkillFact> strengths = facts.stream()
                .filter(SkillFact::requirementSatisfied)
                .sorted(Comparator
                        .comparingInt((SkillFact fact) -> typePriority(fact.skillType()))
                        .thenComparing(SkillFact::baseContributionScore, Comparator.reverseOrder())
                        .thenComparing(SkillFact::matchRate, Comparator.reverseOrder())
                        .thenComparing(SkillFact::skillId))
                .limit(LIMIT)
                .toList();
        List<SkillFact> gaps = facts.stream()
                .filter(fact -> !fact.requirementSatisfied())
                .sorted(Comparator
                        .comparingInt((SkillFact fact) -> typePriority(fact.skillType()))
                        .thenComparing(SkillFact::scoreGap, Comparator.reverseOrder())
                        .thenComparing(SkillFact::requiredLevel, Comparator.reverseOrder())
                        .thenComparing(SkillFact::skillId))
                .limit(LIMIT)
                .toList();
        return new Selection(strengths, gaps);
    }

    private SkillFact fromEvaluated(EvaluatedSkillDetail detail) {
        return new SkillFact(
                detail.skillId(), detail.skillName(), detail.skillType(), detail.userLevel(),
                detail.requiredLevel(), detail.matchRate(), detail.userImportant(),
                detail.requirementSatisfied(), detail.baseMaxScore(), detail.baseContributionScore()
        );
    }

    private SkillFact fromPersisted(JobRecommendationSkillDetail detail) {
        return new SkillFact(
                detail.getSkill().getId(), detail.getSkill().getName(), detail.getSkillType(),
                detail.getUserLevel(), detail.getRequiredLevel(), detail.getMatchRate(),
                detail.isUserImportant(), detail.isRequirementSatisfied(), detail.getBaseMaxScore(),
                detail.getBaseContributionScore()
        );
    }

    private int typePriority(JobPostingSkillType type) {
        return switch (type) {
            case REQUIRED -> 0;
            case PREFERRED -> 1;
            case RELATED -> 2;
        };
    }

    public record SkillFact(
            Long skillId,
            String skillName,
            JobPostingSkillType skillType,
            Short userLevel,
            short requiredLevel,
            BigDecimal matchRate,
            boolean important,
            boolean requirementSatisfied,
            BigDecimal baseMaxScore,
            BigDecimal baseContributionScore
    ) {
        public BigDecimal scoreGap() {
            return baseMaxScore.subtract(baseContributionScore);
        }
    }

    public record Selection(List<SkillFact> strengths, List<SkillFact> gaps) {
        public Selection {
            strengths = List.copyOf(strengths);
            gaps = List.copyOf(gaps);
        }
    }
}

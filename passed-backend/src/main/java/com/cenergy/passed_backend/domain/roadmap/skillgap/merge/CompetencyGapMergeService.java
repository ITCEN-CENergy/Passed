package com.cenergy.passed_backend.domain.roadmap.skillgap.merge;

import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.CompetencyGapMergeInput;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.CompetencyGapSource;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedCompetencyGap;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompetencyGapMergeService {
    private static final Logger log = LoggerFactory.getLogger(CompetencyGapMergeService.class);
    private static final Comparator<CompetencyGapSource> SOURCE_ORDER = Comparator
            .comparing(CompetencyGapSource::jobPostingId)
            .thenComparing(CompetencyGapSource::reportId,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
    private static final Comparator<MergedCandidate> RESULT_ORDER = Comparator
            .comparingInt((MergedCandidate candidate) -> candidate.gapLevel() > 0 ? 1 : 0).reversed()
            .thenComparing(Comparator.comparingInt(MergedCandidate::priorityScore).reversed())
            .thenComparing(Comparator.comparingInt(MergedCandidate::targetLevel).reversed())
            .thenComparing(MergedCandidate::standardCompetencyId);

    private final CompetencyPriorityPolicy priorityPolicy;

    public CompetencyGapMergeService(CompetencyPriorityPolicy priorityPolicy) {
        this.priorityPolicy = priorityPolicy;
    }

    public List<MergedCompetencyGap> merge(List<CompetencyGapMergeInput> inputs) {
        invalidIf(inputs == null, "merge inputs must not be null");

        Map<SourceKey, CompetencyGapSource> uniqueSources = new LinkedHashMap<>();
        for (CompetencyGapMergeInput input : inputs) {
            validateInput(input);
            for (ValidatedCompetencyGap gap : input.competencies()) {
                validateGap(gap);
                CompetencyGapSource source = toSource(input, gap);
                SourceKey key = new SourceKey(input.jobPostingId(), gap.standardCompetencyId());
                CompetencyGapSource previous = uniqueSources.putIfAbsent(key, source);
                invalidIf(previous != null && !previous.equals(source),
                        "conflicting duplicate source for jobPostingId=" + input.jobPostingId()
                                + ", standardCompetencyId=" + gap.standardCompetencyId());
            }
        }

        Map<Long, List<CompetencyGapSource>> sourcesByCompetency = uniqueSources.values().stream()
                .collect(Collectors.groupingBy(CompetencyGapSource::standardCompetencyId));

        List<MergedCandidate> candidates = sourcesByCompetency.values().stream()
                .map(this::mergeGroup)
                .filter(candidate -> candidate.currentLevel() <= candidate.targetLevel())
                .sorted(RESULT_ORDER)
                .toList();

        List<MergedCompetencyGap> result = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            result.add(candidates.get(index).toResult(index + 1));
        }
        return List.copyOf(result);
    }

    private MergedCandidate mergeGroup(List<CompetencyGapSource> unorderedSources) {
        List<CompetencyGapSource> sources = unorderedSources.stream().sorted(SOURCE_ORDER).toList();
        CompetencyGapSource reference = sources.getFirst();
        for (CompetencyGapSource source : sources) {
            invalidIf(!reference.standardCompetencyName().equals(source.standardCompetencyName()),
                    "inconsistent standardCompetencyName for standardCompetencyId="
                            + reference.standardCompetencyId());
            invalidIf(reference.category() != source.category(),
                    "inconsistent category for standardCompetencyId=" + reference.standardCompetencyId());
        }

        int currentLevel = sources.stream().mapToInt(CompetencyGapSource::currentLevel).max().orElseThrow();
        int targetLevel = sources.stream().mapToInt(CompetencyGapSource::targetLevel).max().orElseThrow();
        Set<Integer> currentLevels = sources.stream()
                .map(CompetencyGapSource::currentLevel).collect(Collectors.toSet());
        if (currentLevels.size() > 1) {
            log.warn("Different current levels merged: standardCompetencyId={}, currentLevels={}, selectedCurrentLevel={}",
                    reference.standardCompetencyId(), currentLevels.stream().sorted().toList(), currentLevel);
        }

        RequirementType requirementType = priorityPolicy.majorityOf(
                sources.stream().map(CompetencyGapSource::requirementType).toList());
        int gapLevel = Math.max(targetLevel - currentLevel, 0);
        int frequency = Math.toIntExact(sources.stream()
                .map(CompetencyGapSource::jobPostingId).distinct().count());
        int priorityScore = priorityPolicy.calculateScore(requirementType, gapLevel, frequency);

        return new MergedCandidate(reference.standardCompetencyId(), reference.standardCompetencyName(),
                reference.category(), currentLevel, targetLevel, requirementType, gapLevel, frequency,
                priorityScore, sources);
    }

    private CompetencyGapSource toSource(CompetencyGapMergeInput input, ValidatedCompetencyGap gap) {
        int calculatedGapLevel = Math.max(gap.targetLevel() - gap.currentLevel(), 0);
        return new CompetencyGapSource(input.jobPostingId(), input.reportId(), gap.standardCompetencyId(),
                gap.standardCompetencyName(), gap.category(), gap.currentLevel(), gap.currentEvidence(),
                gap.requirementType(), gap.targetLevel(), calculatedGapLevel);
    }

    private void validateInput(CompetencyGapMergeInput input) {
        invalidIf(input == null, "merge input item must not be null");
        invalidIf(input.jobPostingId() == null || input.jobPostingId() <= 0,
                "jobPostingId must be positive");
        invalidIf(input.competencies() == null, "competencies must not be null");
    }

    private void validateGap(ValidatedCompetencyGap gap) {
        invalidIf(gap == null, "competency gap item must not be null");
        invalidIf(gap.standardCompetencyId() == null || gap.standardCompetencyId() <= 0,
                "standardCompetencyId must be positive");
        invalidIf(gap.standardCompetencyName() == null || gap.standardCompetencyName().isBlank(),
                "standardCompetencyName must not be blank");
        invalidIf(gap.category() == null, "category must not be null");
        invalidIf(gap.requirementType() == null, "requirementType must not be null");
        invalidIf(gap.currentLevel() < 0, "currentLevel must be non-negative");
        invalidIf(gap.targetLevel() < 0, "targetLevel must be non-negative");
    }

    private void invalidIf(boolean invalid, String message) {
        if (invalid) {
            throw new SkillGapException(ErrorCode.SKILL_GAP_INVALID_RESPONSE, message);
        }
    }

    private record SourceKey(Long jobPostingId, Long standardCompetencyId) {
    }

    private record MergedCandidate(
            Long standardCompetencyId,
            String standardCompetencyName,
            CompetencyCategory category,
            int currentLevel,
            int targetLevel,
            RequirementType requirementType,
            int gapLevel,
            int frequency,
            int priorityScore,
            List<CompetencyGapSource> sources
    ) {
        MergedCompetencyGap toResult(int priority) {
            return new MergedCompetencyGap("competency-" + standardCompetencyId, standardCompetencyId,
                    standardCompetencyName, category, currentLevel, targetLevel, requirementType, gapLevel,
                    frequency, priorityScore, priority, sources);
        }
    }
}

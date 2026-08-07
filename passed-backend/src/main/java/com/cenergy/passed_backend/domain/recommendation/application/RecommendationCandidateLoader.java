package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingSkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
@Transactional(readOnly = true)
public class RecommendationCandidateLoader {
    private static final int SKILL_LOAD_BATCH_SIZE = 500;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingSkillRepository jobPostingSkillRepository;

    public RecommendationCandidateLoader(
            JobPostingRepository jobPostingRepository,
            JobPostingSkillRepository jobPostingSkillRepository
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobPostingSkillRepository = jobPostingSkillRepository;
    }

    public Map<Long, PostingSkillBundle> loadByJobRoleIds(Collection<Long> jobRoleIds) {
        List<Long> normalizedJobRoleIds = normalizeJobRoleIds(jobRoleIds);
        if (normalizedJobRoleIds.isEmpty()) {
            return Map.of();
        }

        List<Long> candidateIds = normalizeCandidateIds(
                jobPostingRepository.findCandidateIdsByJobRoleIds(normalizedJobRoleIds)
        );
        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, NormalizedPostingSkill>> skillsByPosting = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            skillsByPosting.put(candidateId, new LinkedHashMap<>());
        }

        for (int start = 0; start < candidateIds.size(); start += SKILL_LOAD_BATCH_SIZE) {
            int end = Math.min(start + SKILL_LOAD_BATCH_SIZE, candidateIds.size());
            List<JobPostingSkill> batch = jobPostingSkillRepository
                    .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(
                            candidateIds.subList(start, end)
                    );
            mergeBatch(skillsByPosting, batch);
        }

        Map<Long, PostingSkillBundle> result = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            result.put(candidateId, toBundle(skillsByPosting.get(candidateId).values()));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Long> normalizeJobRoleIds(Collection<Long> jobRoleIds) {
        if (jobRoleIds == null) {
            throw new IllegalArgumentException("jobRoleIds must not be null");
        }
        if (jobRoleIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("jobRoleIds must contain only positive values");
        }
        return List.copyOf(new TreeSet<>(jobRoleIds));
    }

    private List<Long> normalizeCandidateIds(List<Long> candidateIds) {
        if (candidateIds == null) {
            throw new IllegalStateException("candidate query result must not be null");
        }
        if (candidateIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("candidate query returned an invalid jobPostingId");
        }
        return List.copyOf(new TreeSet<>(candidateIds));
    }

    private void mergeBatch(
            Map<Long, Map<Long, NormalizedPostingSkill>> skillsByPosting,
            List<JobPostingSkill> batch
    ) {
        if (batch == null) {
            throw new IllegalStateException("job posting skill query result must not be null");
        }
        for (JobPostingSkill value : batch) {
            Long jobPostingId = value.getJobPosting().getId();
            Long skillId = value.getSkill().getId();
            Map<Long, NormalizedPostingSkill> postingSkills = skillsByPosting.get(jobPostingId);
            if (postingSkills == null) {
                throw new IllegalStateException("skill query returned a non-candidate job posting");
            }

            NormalizedPostingSkill candidate = new NormalizedPostingSkill(
                    skillId,
                    value.getSkillLevel(),
                    value.getSkillType()
            );
            postingSkills.merge(skillId, candidate, this::higherPriority);
        }
    }

    private NormalizedPostingSkill higherPriority(
            NormalizedPostingSkill current,
            NormalizedPostingSkill candidate
    ) {
        int priorityComparison = Integer.compare(
                priority(candidate.skillType()),
                priority(current.skillType())
        );
        if (priorityComparison > 0) {
            return candidate;
        }
        if (priorityComparison == 0 && candidate.requiredLevel() > current.requiredLevel()) {
            return candidate;
        }
        return current;
    }

    private int priority(JobPostingSkillType skillType) {
        return switch (skillType) {
            case REQUIRED -> 3;
            case PREFERRED -> 2;
            case RELATED -> 1;
        };
    }

    private PostingSkillBundle toBundle(Collection<NormalizedPostingSkill> normalizedSkills) {
        List<PostingSkillBundle.PostingSkill> required = new ArrayList<>();
        List<PostingSkillBundle.PostingSkill> preferred = new ArrayList<>();
        List<PostingSkillBundle.PostingSkill> related = new ArrayList<>();

        normalizedSkills.stream()
                .sorted(Comparator.comparing(NormalizedPostingSkill::skillId))
                .forEach(value -> {
                    PostingSkillBundle.PostingSkill skill = new PostingSkillBundle.PostingSkill(
                            value.skillId(),
                            value.requiredLevel()
                    );
                    switch (value.skillType()) {
                        case REQUIRED -> required.add(skill);
                        case PREFERRED -> preferred.add(skill);
                        case RELATED -> related.add(skill);
                    }
                });
        return new PostingSkillBundle(required, preferred, related);
    }

    private record NormalizedPostingSkill(
            Long skillId,
            short requiredLevel,
            JobPostingSkillType skillType
    ) {
    }
}

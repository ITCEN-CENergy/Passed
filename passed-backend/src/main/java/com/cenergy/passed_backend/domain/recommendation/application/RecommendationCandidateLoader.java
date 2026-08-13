package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingSkillRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
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
        // 직무 id 검증
        List<Long> normalizedJobRoleIds = normalizeJobRoleIds(jobRoleIds);
        if (normalizedJobRoleIds.isEmpty()) {
            return Map.of();
        }

        // 직무 id로 후보 공고 id 조회
        List<Long> candidateIds = normalizeCandidateIds(
                jobPostingRepository.findCandidateIdsByJobRoleIds(normalizedJobRoleIds)
        );
        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        // 후보 공고 id로 공고와 공고의 전체 스킬을 묶음
        Map<Long, Map<Long, NormalizedPostingSkill>> skillsByPosting = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            skillsByPosting.put(candidateId, new LinkedHashMap<>());
        }

        // 공고의 스킬별로 NormalizedPostingSkill로 가공해서 Map<Long, NormalizedPostingSkill> 생성
        for (int start = 0; start < candidateIds.size(); start += SKILL_LOAD_BATCH_SIZE) {
            int end = Math.min(start + SKILL_LOAD_BATCH_SIZE, candidateIds.size());
            List<JobPostingSkill> batch = jobPostingSkillRepository
                    .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(
                            candidateIds.subList(start, end)
                    );
            // JobPostingSkill별로 NormalizedPostingSkill로 처리함
            mergeBatch(skillsByPosting, batch);
        }

        // 후보 공고별 스킬 타입 구분한 번들 생성
        Map<Long, PostingSkillBundle> result = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            result.put(candidateId, toBundle(skillsByPosting.get(candidateId).values()));
        }
        return Collections.unmodifiableMap(result);
    }

    public PostingSkillBundle loadByJobPostingId(Long jobPostingId) {

        // 후보 공고 id로 공고와 공고의 전체 스킬을 묶음
        Map<Long, Map<Long, NormalizedPostingSkill>> skillsByPosting = new LinkedHashMap<>();
        skillsByPosting.put(jobPostingId, new LinkedHashMap<>());


        // 공고의 스킬별로 NormalizedPostingSkill로 가공해서 Map<Long, NormalizedPostingSkill> 생성
        List<JobPostingSkill> batch = jobPostingSkillRepository.findAllByJobPostingId(jobPostingId);
        // JobPostingSkill별로 NormalizedPostingSkill로 처리함
        mergeBatch(skillsByPosting, batch);

        return toBundle(skillsByPosting.get(jobPostingId).values());
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
        // 직무 ID 검증 후 중복 제거 및 오름차순 정렬
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
                    value.getSkill().getName(),
                    value.getSkill().getCategory(),
                    value.getSkillLevel(),
                    value.getSkillType()
            );
            postingSkills.merge(skillId, candidate, this::higherPriority);
        }
    }

//    스킬 우선순위 결정
//    1순위: REQUIRED > PREFERRED > RELATED
//    2순위: 같은 타입이면 requiredLevel이 높은 것
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
                            value.skillName(),
                            value.skillCategory(),
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
            String skillName,
            SkillCategory skillCategory,
            short requiredLevel,
            JobPostingSkillType skillType
    ) {
    }
}

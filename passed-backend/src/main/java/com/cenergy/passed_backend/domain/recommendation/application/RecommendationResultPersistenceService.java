package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.*;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecommendationResultPersistenceService {
    private final RecommendationRunRepository runRepository;
    private final JobPostingRepository jobPostingRepository;
    private final SkillRepository skillRepository;
    private final JobRecommendationRepository recommendationRepository;
    private final JobRecommendationSkillDetailRepository skillDetailRepository;

    public RecommendationResultPersistenceService(
            RecommendationRunRepository runRepository,
            JobPostingRepository jobPostingRepository,
            SkillRepository skillRepository,
            JobRecommendationRepository recommendationRepository,
            JobRecommendationSkillDetailRepository skillDetailRepository
    ) {
        this.runRepository = runRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.skillRepository = skillRepository;
        this.recommendationRepository = recommendationRepository;
        this.skillDetailRepository = skillDetailRepository;
    }

    @Transactional
    public void complete(
            Long recommendationRunId,
            List<RankedRecommendation> rankedRecommendations,
            Map<Long, RecommendationExplanation> explanations
    ) {
        Objects.requireNonNull(rankedRecommendations, "rankedRecommendations must not be null");
        Objects.requireNonNull(explanations, "explanations must not be null");
        // 추천 실행 이력을 비관적 락으로 조회하여 동일 추천 실행에 대한 동시 완료 처리를 방지
        RecommendationRun run = runRepository.findByIdForUpdate(recommendationRunId)
                .orElseThrow(() -> new IllegalStateException("Recommendation run not found"));
        if (rankedRecommendations.isEmpty()) {
            run.complete();
            return;
        }

        List<Long> postingIds = rankedRecommendations.stream()
                .map(RankedRecommendation::jobPostingId)
                .toList();
        // 선택된 공고를 한 번에 조회한 뒤, 공고 ID로 빠르게 접근할 수 있도록 Map으로 변환
        Map<Long, JobPosting> postings = indexPostings(
                jobPostingRepository.findAllById(postingIds),
                postingIds.size()
        );
        List<Long> skillIds = rankedRecommendations.stream()
                .flatMap(value -> value.recommendation().score().skillDetails().stream())
                .map(EvaluatedSkillDetail::skillId)
                .distinct()
                .toList();
        // 추천 상세에 사용된 스킬을 한 번에 조회한 뒤, 스킬 ID 기준 Map으로 변환
        Map<Long, Skill> skills = indexSkills(skillRepository.findAllByIdIn(skillIds), skillIds.size());

        // 공고별 최종 추천 결과 엔티티 생성 후 일괄 저장
        Map<Long, JobRecommendation> recommendationsByPostingId = new LinkedHashMap<>();
        for (RankedRecommendation ranked : rankedRecommendations) {
            RecommendationExplanation explanation = Objects.requireNonNull(
                    explanations.get(ranked.jobPostingId()),
                    "Explanation must exist for every selected posting"
            );
            GradedRecommendation graded = ranked.recommendation();
            RecommendationScoreResult score = graded.score();
            JobRecommendation entity = JobRecommendation.create(
                    run,
                    postings.get(ranked.jobPostingId()),
                    score.totalScore(),
                    score.requiredScore(),
                    score.preferredScore(),
                    score.relatedScore(),
                    score.importantSkillBonus(),
                    score.requiredSkillCount(),
                    score.requiredOwnedCount(),
                    score.requiredCoverageRate(),
                    score.requiredLevelMatchRate(),
                    score.importantSkillCount(),
                    score.importantMatchCount(),
                    score.candidateTier(),
                    graded.grade(),
                    ranked.rankOrder(),
                    explanation.reason()
            );
            recommendationsByPostingId.put(ranked.jobPostingId(), entity);
        }
        recommendationRepository.saveAll(recommendationsByPostingId.values());
        recommendationRepository.flush();

        // 각 추천 공고의 스킬별 평가 상세 엔티티를 생성하여 일괄 저장
        List<JobRecommendationSkillDetail> details = rankedRecommendations.stream()
                .flatMap(ranked -> ranked.recommendation().score().skillDetails().stream()
                        .map(detail -> toEntity(
                                recommendationsByPostingId.get(ranked.jobPostingId()),
                                skills.get(detail.skillId()),
                                detail
                        )))
                .toList();
        skillDetailRepository.saveAll(details);
        // 추천 결과와 상세 저장이 정상적으로 끝나면 추천 실행 이력의 상태를 완료 상태로 변경
        run.complete();
    }

    private JobRecommendationSkillDetail toEntity(
            JobRecommendation recommendation,
            Skill skill,
            EvaluatedSkillDetail detail
    ) {
        return JobRecommendationSkillDetail.create(
                recommendation,
                Objects.requireNonNull(skill, "Skill must exist for every detail"),
                detail.skillType(),
                detail.requiredLevel(),
                detail.userLevel(),
                detail.evaluationType(),
                detail.owned(),
                detail.requirementSatisfied(),
                detail.userImportant(),
                detail.matchRate(),
                detail.baseMaxScore(),
                detail.baseContributionScore(),
                detail.importantBonusContributionScore()
        );
    }

    private Map<Long, JobPosting> indexPostings(Collection<JobPosting> values, int expectedSize) {
        Map<Long, JobPosting> result = new LinkedHashMap<>();
        for (JobPosting value : values) {
            result.put(value.getId(), value);
        }
        if (result.size() != expectedSize) {
            throw new IllegalStateException("A selected job posting no longer exists");
        }
        return result;
    }

    private Map<Long, Skill> indexSkills(Collection<Skill> values, int expectedSize) {
        Map<Long, Skill> result = new LinkedHashMap<>();
        for (Skill value : values) {
            result.put(value.getId(), value);
        }
        if (result.size() != expectedSize) {
            throw new IllegalStateException("A selected recommendation skill no longer exists");
        }
        return result;
    }
}

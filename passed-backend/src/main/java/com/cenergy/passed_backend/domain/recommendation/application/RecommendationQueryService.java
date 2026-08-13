package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSummaryResponse;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.recommendation.dto.*;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.exception.RecommendationException;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.user.dto.JobRoleResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RecommendationQueryService {
    private static final BigDecimal ZERO_RATE = new BigDecimal("0.0000");

    private final RecommendationRunRepository runRepository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final JobRecommendationRepository recommendationRepository;
    private final JobRecommendationSkillDetailRepository skillDetailRepository;
    private final SkillRepository skillRepository;
    private final RecommendationSkillHighlightSelector highlightSelector;

    public RecommendationQueryService(
            CurrentUserIdProvider currentUserIdProvider,
            RecommendationRunRepository runRepository,
            JobRecommendationRepository recommendationRepository,
            JobRecommendationSkillDetailRepository skillDetailRepository,
            SkillRepository skillRepository,
            RecommendationSkillHighlightSelector highlightSelector
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.runRepository = runRepository;
        this.recommendationRepository = recommendationRepository;
        this.skillDetailRepository = skillDetailRepository;
        this.skillRepository = skillRepository;
        this.highlightSelector = highlightSelector;
    }

    public RecommendationHistoryResponse getHistory(RecommendationHistoryRequest request) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        Page<RecommendationRun> page = runRepository.findAllByUserIdOrderByStartedAtDescIdDesc(
                userId, PageRequest.of(request.page(), request.size())
        );
        return new RecommendationHistoryResponse(
                page.getContent().stream().map(this::historyItem).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
        );
    }

    public RecommendationResultResponse getResult(Long runId) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        RecommendationRun run = ownedRun(runId, userId);
        List<RecommendationItemResponse> items = recommendationRepository
                .findAllByRecommendationRunIdOrderByRankOrderAsc(runId)
                .stream()
                .map(this::recommendationItem)
                .toList();
        return new RecommendationResultResponse(runResponse(run), items);
    }

    public RecommendationDetailResponse getDetail(Long runId, Long jobRecommendationId) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        JobRecommendation recommendation = recommendationRepository
                .findByIdAndRecommendationRunIdAndRecommendationRunUserId(
                        jobRecommendationId, runId, userId
                )
                .orElseThrow(() -> new RecommendationException(
                        ErrorCode.RECOMMENDATION_RESULT_NOT_FOUND,
                        "Recommendation result not found"
                ));
        List<JobRecommendationSkillDetail> details = skillDetailRepository
                .findAllByJobRecommendationIdOrderByIdAsc(jobRecommendationId);
        RecommendationSkillHighlightSelector.Selection highlights =
                highlightSelector.selectPersisted(details);
        RecommendationReportResponse report = new RecommendationReportResponse(
                recommendation.getRecommendationGrade(),
                recommendation.getTotalScore(),
                recommendation.getReason(),
                skillGroups(details),
                highlights.strengths().stream().map(this::highlight).toList(),
                highlights.gaps().stream().map(this::highlight).toList()
        );
        return new RecommendationDetailResponse(
                runId,
                recommendation.getId(),
                recommendation.getRankOrder(),
                postingDetail(recommendation.getJobPosting()),
                report
        );
    }

    public RecommendationUserSkillsResponse getUserSkills(Long runId) {
        Long userId = currentUserIdProvider.getCurrentUserId();
        RecommendationRun run = ownedRun(runId, userId);
        List<Map<String, Object>> snapshots = mapList(run.getUserSkillSnapshot().get("skills"));
        List<Long> skillIds = snapshots.stream()
                .map(value -> longValue(value.get("skillId")))
                .toList();
        Map<Long, Skill> skillsById = new LinkedHashMap<>();
        for (Skill skill : skillRepository.findAllByIdIn(skillIds)) {
            skillsById.put(skill.getId(), skill);
        }
        List<UserSkillSnapshotResponse> skills = snapshots.stream().map(value -> {
            Long skillId = longValue(value.get("skillId"));
            Skill skill = skillsById.get(skillId);
            if (skill == null) {
                throw new IllegalStateException("Snapshot skill no longer exists: " + skillId);
            }
            return new UserSkillSnapshotResponse(
                    skillId,
                    skill.getName(),
                    skill.getCategory() == null ? null : skill.getCategory().name(),
                    numberValue(value.get("skillLevel")).shortValue(),
                    booleanValue(value.get("isImportant"))
            );
        }).toList();
        return new RecommendationUserSkillsResponse(runId, skills);
    }

    private RecommendationRun ownedRun(Long runId, Long userId) {
        return runRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new RecommendationException(
                        ErrorCode.RECOMMENDATION_RUN_NOT_FOUND,
                        "Recommendation run not found"
                ));
    }

    private RecommendationHistoryItemResponse historyItem(RecommendationRun run) {
        return new RecommendationHistoryItemResponse(
                run.getId(), run.getStatus(), preference(run), run.getStartedAt()
        );
    }

    private RecommendationRunResponse runResponse(RecommendationRun run) {
        List<Map<String, Object>> skills = mapList(run.getUserSkillSnapshot().get("skills"));
        int importantCount = (int) skills.stream()
                .filter(value -> booleanValue(value.get("isImportant")))
                .count();
        return new RecommendationRunResponse(
                run.getId(),
                run.getStatus(),
                preference(run),
                new RecommendationMetricsResponse(
                        skills.size(), importantCount, run.getCandidatePostingCount(),
                        run.getRequiredQualifiedPostingCount()
                ),
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }

    private RecommendationPreferenceResponse preference(RecommendationRun run) {
        Map<String, Object> snapshot = run.getPreferenceSnapshot();
        List<JobRoleResponse> roles = mapList(snapshot.get("jobRoles")).stream()
                .map(value -> new JobRoleResponse(
                        longValue(value.get("jobRoleId")), textValue(value.get("jobRoleName"))
                ))
                .toList();
        return new RecommendationPreferenceResponse(
                longValue(snapshot.get("industryId")),
                textValue(snapshot.get("industryName")),
                roles
        );
    }

    private RecommendationItemResponse recommendationItem(JobRecommendation value) {
        JobPosting posting = value.getJobPosting();
        return new RecommendationItemResponse(
                value.getId(), value.getRankOrder(), value.getRecommendationGrade(),
                value.getTotalScore(), value.getReason(),
                new JobPostingSummaryResponse(
                        posting.getId(), posting.getTitle(), posting.getRegion(),
                        posting.getCompany().getCompanyName(), posting.getJobRole().getJobRoleName(),
                        posting.getJobRole().getIndustry().getIndustryName()
                )
        );
    }

    private JobPostingDetailResponse postingDetail(JobPosting posting) {
        return new JobPostingDetailResponse(
                posting.getId(), posting.getTitle(),
                posting.getJobRole().getIndustry().getIndustryName(),
                posting.getJobRole().getJobRoleName(),
                posting.getCompany().getCompanyName(),
                posting.getCompany().getCompanySize().getLabel(),
                posting.getRegion(), posting.getCareerType(), posting.getHireType(),
                posting.getEducationLevel(), posting.getPositionDetail(), posting.getMainDuty(),
                posting.getQualification(), posting.getPreference(), posting.getDisqualifyReason(),
                posting.getProcess(), posting.getCompany().getBenefits()
        );
    }

    private List<SkillGroupResponse> skillGroups(List<JobRecommendationSkillDetail> details) {
        Map<JobPostingSkillType, List<JobRecommendationSkillDetail>> byType =
                new EnumMap<>(JobPostingSkillType.class);
        for (JobPostingSkillType type : JobPostingSkillType.values()) {
            byType.put(type, new ArrayList<>());
        }
        for (JobRecommendationSkillDetail detail : details) {
            byType.get(detail.getSkillType()).add(detail);
        }
        List<SkillGroupResponse> groups = new ArrayList<>();
        for (JobPostingSkillType type : JobPostingSkillType.values()) {
            List<JobRecommendationSkillDetail> typed = byType.get(type);
            List<SkillMatchResponse> skills = typed.stream()
                    .map(detail -> new SkillMatchResponse(
                            detail.getSkill().getId(), detail.getSkill().getName(), detail.isOwned(),
                            detail.isUserImportant(), rate(detail.getMatchRate())
                    ))
                    .toList();
            int ownedCount = (int) typed.stream().filter(JobRecommendationSkillDetail::isOwned).count();
            BigDecimal average = typed.isEmpty()
                    ? ZERO_RATE
                    : typed.stream()
                    .map(JobRecommendationSkillDetail::getMatchRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(typed.size()), 4, RoundingMode.HALF_UP);
            groups.add(new SkillGroupResponse(type, rate(average), ownedCount, typed.size(), skills));
        }
        return List.copyOf(groups);
    }

    private HighlightedSkillResponse highlight(
            RecommendationSkillHighlightSelector.SkillFact fact
    ) {
        return new HighlightedSkillResponse(fact.skillId(), fact.skillName(), fact.important(), fact.matchRate());
    }

    private BigDecimal rate(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private Long longValue(Object value) {
        return value == null ? null : numberValue(value).longValue();
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

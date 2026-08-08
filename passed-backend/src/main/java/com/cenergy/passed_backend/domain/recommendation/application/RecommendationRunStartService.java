package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationRunContext;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationPolicyStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.recommendation.exception.RecommendationException;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationGradeRuleRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationScoringPolicyRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class RecommendationRunStartService {
    private static final String POLICY_CODE = "SKILL_MATCH";
    private static final String POLICY_VERSION = "v1";

    private final UserRepository userRepository;
    private final RecommendationRunRepository runRepository;
    private final RecommendationScoringPolicyRepository policyRepository;
    private final RecommendationGradeRuleRepository gradeRuleRepository;
    private final SkillRepository skillRepository;
    private final JobRoleRepository jobRoleRepository;
    private final UserSkillProvider userSkillProvider;
    private final RecommendationSnapshotFactory snapshotFactory;

    public RecommendationRunStartService(
            UserRepository userRepository,
            RecommendationRunRepository runRepository,
            RecommendationScoringPolicyRepository policyRepository,
            RecommendationGradeRuleRepository gradeRuleRepository,
            SkillRepository skillRepository,
            JobRoleRepository jobRoleRepository,
            UserSkillProvider userSkillProvider,
            RecommendationSnapshotFactory snapshotFactory
    ) {
        this.userRepository = userRepository;
        this.runRepository = runRepository;
        this.policyRepository = policyRepository;
        this.gradeRuleRepository = gradeRuleRepository;
        this.skillRepository = skillRepository;
        this.jobRoleRepository = jobRoleRepository;
        this.userSkillProvider = userSkillProvider;
        this.snapshotFactory = snapshotFactory;
    }

    @Transactional
    public RecommendationRunContext start(RecommendationPrepareRequest request) {
        NormalizedRequest normalized = normalize(request);
        User user = lockUser(normalized.userId());
        rejectConcurrentRun(normalized.userId());
        RecommendationScoringPolicy policy = loadPolicy();
        List<RecommendationGradeRule> gradeRules = loadGradeRules(policy.getId());
        List<UserSkillData> userSkills = loadAndValidateUserSkills(normalized.userId());
        validateCommonSkills(userSkills);
        List<RecommendationSnapshotFactory.JobRoleSnapshot> jobRoles = loadAndValidateJobRoles(
                normalized
        );
        RecommendationSnapshotFactory.SnapshotResult snapshots = snapshotFactory.create(
                userSkills,
                normalized.industryId(),
                jobRoles
        );
        RecommendationRun run = runRepository.save(RecommendationRun.startProcessing(
                user,
                policy,
                snapshots.userSkillSnapshotHash(),
                snapshots.userSkillSnapshot(),
                snapshots.preferenceSnapshot()
        ));
        int importantSkillCount = (int) userSkills.stream()
                .filter(UserSkillData::important)
                .count();
        return new RecommendationRunContext(
                run.getId(),
                policy,
                gradeRules,
                userSkills,
                importantSkillCount,
                snapshots.userSkillSnapshotHash(),
                normalized.industryId(),
                normalized.jobRoleIds()
        );
    }

    private NormalizedRequest normalize(RecommendationPrepareRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0
                || request.industryId() == null || request.industryId() <= 0
                || request.jobRoleIds() == null
                || request.jobRoleIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_INVALID_REQUEST,
                    "userId, industryId and jobRoleIds must be valid"
            );
        }
        return new NormalizedRequest(
                request.userId(),
                request.industryId(),
                List.copyOf(new TreeSet<>(request.jobRoleIds()))
        );
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RecommendationException(
                        ErrorCode.RECOMMENDATION_USER_NOT_FOUND,
                        "Recommendation user not found"
                ));
    }

    private void rejectConcurrentRun(Long userId) {
        if (runRepository.existsByUserIdAndStatus(userId, RecommendationRunStatus.PROCESSING)) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_ALREADY_PROCESSING,
                    "A recommendation run is already processing for this user"
            );
        }
    }

    private RecommendationScoringPolicy loadPolicy() {
        return policyRepository.findByPolicyCodeAndVersionAndStatus(
                        POLICY_CODE,
                        POLICY_VERSION,
                        RecommendationPolicyStatus.ACTIVE
                )
                .orElseThrow(() -> new RecommendationException(
                        ErrorCode.RECOMMENDATION_POLICY_NOT_FOUND,
                        "Active SKILL_MATCH v1 policy not found"
                ));
    }

    private List<RecommendationGradeRule> loadGradeRules(Long policyId) {
        List<RecommendationGradeRule> rules = gradeRuleRepository
                .findAllByScoringPolicyIdOrderByPriorityDesc(policyId);
        if (rules.size() != RecommendationGrade.values().length) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_POLICY_CONFIGURATION_INVALID,
                    "The recommendation policy must have every grade rule"
            );
        }
        return List.copyOf(rules);
    }

    private List<UserSkillData> loadAndValidateUserSkills(Long userId) {
        List<UserSkillData> values = userSkillProvider.findByUserId(userId);
        if (values == null || values.isEmpty()) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_USER_SKILLS_NOT_FOUND,
                    "User skills not found"
            );
        }
        for (UserSkillData skill : values) {
            if (skill == null || skill.skillId() == null || skill.skillId() <= 0
                    || skill.skillLevel() < 1 || skill.skillLevel() > 3) {
                throw new RecommendationException(
                        ErrorCode.RECOMMENDATION_SKILL_DATA_INVALID,
                        "User skill data contains an invalid skill"
                );
            }
        }
        List<UserSkillData> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(UserSkillData::skillId));
        Long previousSkillId = null;
        for (UserSkillData skill : sorted) {
            if (skill.skillId().equals(previousSkillId)) {
                throw new RecommendationException(
                        ErrorCode.RECOMMENDATION_SKILL_DATA_INVALID,
                        "User skill data contains a duplicated skill"
                );
            }
            previousSkillId = skill.skillId();
        }
        return List.copyOf(sorted);
    }

    private void validateCommonSkills(List<UserSkillData> userSkills) {
        List<Long> skillIds = userSkills.stream().map(UserSkillData::skillId).toList();
        Map<Long, Skill> commonSkills = new LinkedHashMap<>();
        for (Skill skill : skillRepository.findAllByIdIn(skillIds)) {
            commonSkills.put(skill.getId(), skill);
        }
        if (commonSkills.size() != skillIds.size()) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_SKILL_DATA_INVALID,
                    "A user skill does not exist in the common skills table"
            );
        }
    }

    private List<RecommendationSnapshotFactory.JobRoleSnapshot> loadAndValidateJobRoles(
            NormalizedRequest request
    ) {
        if (request.jobRoleIds().isEmpty()) {
            return List.of();
        }
        Map<Long, JobRole> rolesById = new LinkedHashMap<>();
        for (JobRole role : jobRoleRepository.findAllByIdIn(request.jobRoleIds())) {
            rolesById.put(role.getId(), role);
        }
        if (rolesById.size() != request.jobRoleIds().size()) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_INVALID_REQUEST,
                    "A requested job role does not exist"
            );
        }
        List<RecommendationSnapshotFactory.JobRoleSnapshot> result = new ArrayList<>();
        for (Long roleId : request.jobRoleIds()) {
            JobRole role = rolesById.get(roleId);
            if (!request.industryId().equals(role.getIndustry().getId())) {
                throw new RecommendationException(
                        ErrorCode.RECOMMENDATION_INVALID_REQUEST,
                        "Every job role must belong to the requested industry"
                );
            }
            result.add(new RecommendationSnapshotFactory.JobRoleSnapshot(
                    role.getId(),
                    role.getJobRoleName(),
                    role.getIndustry().getId(),
                    role.getIndustry().getIndustryName()
            ));
        }
        return List.copyOf(result);
    }

    private record NormalizedRequest(Long userId, Long industryId, List<Long> jobRoleIds) {
    }
}

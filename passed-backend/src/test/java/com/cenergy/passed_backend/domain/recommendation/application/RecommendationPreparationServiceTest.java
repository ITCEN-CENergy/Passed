package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationGradeRuleRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationScoringPolicyRepository;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationPolicyStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.recommendation.exception.RecommendationException;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationPrepareResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationPreparationServiceTest {
    private RecommendationRunRepository runRepository;
    private RecommendationScoringPolicyRepository policyRepository;
    private RecommendationGradeRuleRepository gradeRuleRepository;
    private SkillRepository skillRepository;
    private JobRoleRepository jobRoleRepository;
    private UserRepository userRepository;
    private UserSkillProvider userSkillProvider;
    private RecommendationCandidateSelectionService candidateSelectionService;
    private RecommendationPreparationService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(RecommendationRunRepository.class);
        policyRepository = mock(RecommendationScoringPolicyRepository.class);
        gradeRuleRepository = mock(RecommendationGradeRuleRepository.class);
        skillRepository = mock(SkillRepository.class);
        jobRoleRepository = mock(JobRoleRepository.class);
        userRepository = mock(UserRepository.class);
        userSkillProvider = mock(UserSkillProvider.class);
        candidateSelectionService = mock(RecommendationCandidateSelectionService.class);
        service = new RecommendationPreparationService(
                userRepository,
                runRepository,
                policyRepository,
                gradeRuleRepository,
                skillRepository,
                jobRoleRepository,
                userSkillProvider,
                new RecommendationSnapshotFactory(new ObjectMapper()),
                candidateSelectionService
        );
    }

    @Test
    void normalizesInputBuildsSnapshotsAndStartsProcessingRun() {
        User user = mock(User.class);
        RecommendationScoringPolicy policy = policy();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user));
        when(runRepository.existsByUserIdAndStatus(2L, RecommendationRunStatus.PROCESSING))
                .thenReturn(false);
        when(policyRepository.findByPolicyCodeAndVersionAndStatus(
                "SKILL_MATCH", "v1", RecommendationPolicyStatus.ACTIVE
        )).thenReturn(Optional.of(policy));
        when(gradeRuleRepository.findAllByScoringPolicyIdOrderByPriorityDesc(11L))
                .thenReturn(List.of(
                        mock(RecommendationGradeRule.class),
                        mock(RecommendationGradeRule.class),
                        mock(RecommendationGradeRule.class),
                        mock(RecommendationGradeRule.class)
                ));
        when(userSkillProvider.findByUserId(2L)).thenReturn(List.of(
                new UserSkillData(107L, (short) 3, true),
                new UserSkillData(12L, (short) 3, true),
                new UserSkillData(16L, (short) 1, false)
        ));
        when(skillRepository.findAllByIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(this::skill).toList();
        });
        List<JobRole> jobRoles = List.of(
                role(239L, "AI서비스개발자"),
                role(227L, "AI/ML엔지니어"),
                role(237L, "AI보안전문가")
        );
        when(jobRoleRepository.findAllByIdIn(any())).thenReturn(jobRoles);
        when(runRepository.save(any(RecommendationRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateSelectionService.select(any(), any(), same(policy)))
                .thenReturn(selectionResult(3, 2));

        var response = service.prepare(new RecommendationPrepareRequest(
                2L,
                8L,
                List.of(239L, 237L, 239L, 227L)
        ));

        assertEquals(RecommendationRunStatus.PROCESSING, response.status());
        assertEquals(List.of(227L, 237L, 239L), response.jobRoleIds());
        assertEquals(3, response.userSkillCount());
        assertEquals(2, response.importantSkillCount());
        assertEquals(3, response.candidatePostingCount());
        assertEquals(2, response.requiredQualifiedPostingCount());
        assertTrue(response.userSkillSnapshotHash().matches("^[0-9a-f]{64}$"));

        ArgumentCaptor<RecommendationRun> runCaptor = ArgumentCaptor.forClass(RecommendationRun.class);
        verify(runRepository).save(runCaptor.capture());
        RecommendationRun savedRun = runCaptor.getValue();
        assertEquals(RecommendationRunStatus.PROCESSING, savedRun.getStatus());
        assertEquals(List.of(12L, 16L, 107L), snapshotSkillIds(savedRun));
        assertEquals(List.of(227L, 237L, 239L), savedRun.getPreferenceSnapshot().get("jobRoleIds"));
        verify(jobRoleRepository).findAllByIdIn(List.of(227L, 237L, 239L));
        verify(candidateSelectionService).select(
                eq(List.of(227L, 237L, 239L)),
                any(),
                same(policy)
        );
        InOrder executionOrder = inOrder(runRepository, candidateSelectionService);
        executionOrder.verify(runRepository).save(any(RecommendationRun.class));
        executionOrder.verify(candidateSelectionService).select(any(), any(), same(policy));
    }

    @Test
    void rejectsRequestWhenTheUserAlreadyHasAProcessingRun() {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(mock(User.class)));
        when(runRepository.existsByUserIdAndStatus(2L, RecommendationRunStatus.PROCESSING))
                .thenReturn(true);

        RecommendationException exception = assertThrows(
                RecommendationException.class,
                () -> service.prepare(new RecommendationPrepareRequest(2L, 8L, List.of()))
        );

        assertEquals(ErrorCode.RECOMMENDATION_ALREADY_PROCESSING, exception.getErrorCode());
        verify(policyRepository, never()).findByPolicyCodeAndVersionAndStatus(any(), any(), any());
        verify(runRepository, never()).save(any());
    }

    private RecommendationScoringPolicy policy() {
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        when(policy.getId()).thenReturn(11L);
        when(policy.getPolicyCode()).thenReturn("SKILL_MATCH");
        when(policy.getVersion()).thenReturn("v1");
        return policy;
    }

    private RecommendationCandidateSelectionResult selectionResult(
            int candidateCount,
            int qualifiedCount
    ) {
        Map<Long, PostingSkillBundle> candidates = new LinkedHashMap<>();
        Map<Long, RequiredSkillEvaluation> qualified = new LinkedHashMap<>();
        for (long id = 1; id <= candidateCount; id++) {
            candidates.put(id, PostingSkillBundle.empty());
            if (id <= qualifiedCount) {
                qualified.put(id, mock(RequiredSkillEvaluation.class));
            }
        }
        return new RecommendationCandidateSelectionResult(candidates, qualified);
    }

    private Skill skill(Long id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        return skill;
    }

    private JobRole role(Long id, String name) {
        Industry industry = mock(Industry.class);
        when(industry.getId()).thenReturn(8L);
        when(industry.getIndustryName()).thenReturn("AI·개발·데이터");
        JobRole role = mock(JobRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getJobRoleName()).thenReturn(name);
        when(role.getIndustry()).thenReturn(industry);
        return role;
    }

    @SuppressWarnings("unchecked")
    private List<Long> snapshotSkillIds(RecommendationRun run) {
        List<java.util.Map<String, Object>> skills =
                (List<java.util.Map<String, Object>>) run.getUserSkillSnapshot().get("skills");
        return skills.stream().map(value -> (Long) value.get("skillId")).toList();
    }
}

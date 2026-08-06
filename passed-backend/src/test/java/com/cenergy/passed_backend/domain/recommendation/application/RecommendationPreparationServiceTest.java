package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.api.RecommendationPrepareRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationGradeRuleRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationJobRoleRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationScoringPolicyRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationSkillRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationUserRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.UserSkillProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.jobposting.entity.Industry;
import com.cenergy.passed_backend.jobposting.entity.JobRole;
import com.cenergy.passed_backend.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.recommendation.entity.RecommendationPolicyStatus;
import com.cenergy.passed_backend.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.skill.entity.Skill;
import com.cenergy.passed_backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationPreparationServiceTest {
    private RecommendationUserRepository userRepository;
    private RecommendationRunRepository runRepository;
    private RecommendationScoringPolicyRepository policyRepository;
    private RecommendationGradeRuleRepository gradeRuleRepository;
    private RecommendationSkillRepository skillRepository;
    private RecommendationJobRoleRepository jobRoleRepository;
    private UserSkillProvider userSkillProvider;
    private RecommendationPreparationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(RecommendationUserRepository.class);
        runRepository = mock(RecommendationRunRepository.class);
        policyRepository = mock(RecommendationScoringPolicyRepository.class);
        gradeRuleRepository = mock(RecommendationGradeRuleRepository.class);
        skillRepository = mock(RecommendationSkillRepository.class);
        jobRoleRepository = mock(RecommendationJobRoleRepository.class);
        userSkillProvider = mock(UserSkillProvider.class);
        service = new RecommendationPreparationService(
                userRepository,
                runRepository,
                policyRepository,
                gradeRuleRepository,
                skillRepository,
                jobRoleRepository,
                userSkillProvider,
                new RecommendationSnapshotFactory(new ObjectMapper())
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

        var response = service.prepare(new RecommendationPrepareRequest(
                2L,
                8L,
                List.of(239L, 237L, 239L, 227L)
        ));

        assertEquals(RecommendationRunStatus.PROCESSING, response.status());
        assertEquals(List.of(227L, 237L, 239L), response.jobRoleIds());
        assertEquals(3, response.userSkillCount());
        assertEquals(2, response.importantSkillCount());
        assertTrue(response.userSkillSnapshotHash().matches("^[0-9a-f]{64}$"));

        ArgumentCaptor<RecommendationRun> runCaptor = ArgumentCaptor.forClass(RecommendationRun.class);
        verify(runRepository).save(runCaptor.capture());
        RecommendationRun savedRun = runCaptor.getValue();
        assertEquals(RecommendationRunStatus.PROCESSING, savedRun.getStatus());
        assertEquals(List.of(12L, 16L, 107L), snapshotSkillIds(savedRun));
        assertEquals(List.of(227L, 237L, 239L), savedRun.getPreferenceSnapshot().get("jobRoleIds"));
        verify(jobRoleRepository).findAllByIdIn(List.of(227L, 237L, 239L));
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

package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationRunContext;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationCreateRequest;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGradeRule;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationPolicyStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationScoringPolicy;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationGradeRuleRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationScoringPolicyRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationRunStartServiceTest {
    @Test
    void validatesAndNormalizesInputsBeforeCreatingProcessingRun() {
        UserRepository userRepository = mock(UserRepository.class);
        RecommendationRunRepository runRepository = mock(RecommendationRunRepository.class);
        RecommendationScoringPolicyRepository policyRepository = mock(
                RecommendationScoringPolicyRepository.class
        );
        RecommendationGradeRuleRepository gradeRuleRepository = mock(
                RecommendationGradeRuleRepository.class
        );
        SkillRepository skillRepository = mock(SkillRepository.class);
        JobRoleRepository jobRoleRepository = mock(JobRoleRepository.class);
        UserSkillProvider userSkillProvider = mock(UserSkillProvider.class);
        RecommendationScoringPolicy policy = mock(RecommendationScoringPolicy.class);
        User user = mock(User.class);
        List<RecommendationGradeRule> gradeRules = List.of(
                mock(RecommendationGradeRule.class),
                mock(RecommendationGradeRule.class),
                mock(RecommendationGradeRule.class),
                mock(RecommendationGradeRule.class)
        );
        List<Skill> skills = List.of(skill(10L), skill(20L));
        List<JobRole> roles = List.of(role(227L), role(239L));
        when(policy.getId()).thenReturn(11L);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user));
        when(runRepository.existsByUserIdAndStatus(2L, RecommendationRunStatus.PROCESSING))
                .thenReturn(false);
        when(policyRepository.findByPolicyCodeAndVersionAndStatus(
                "SKILL_MATCH",
                "v1",
                RecommendationPolicyStatus.ACTIVE
        )).thenReturn(Optional.of(policy));
        when(gradeRuleRepository.findAllByScoringPolicyIdOrderByPriorityDesc(11L))
                .thenReturn(gradeRules);
        when(userSkillProvider.findByUserId(2L)).thenReturn(List.of(
                new UserSkillData(20L, (short) 2, false),
                new UserSkillData(10L, (short) 3, true)
        ));
        when(skillRepository.findAllByIdIn(any())).thenReturn(skills);
        when(jobRoleRepository.findAllByIdIn(List.of(227L, 239L))).thenReturn(roles);
        when(runRepository.saveAndFlush(any(RecommendationRun.class))).thenAnswer(invocation -> {
            RecommendationRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 10L);
            ReflectionTestUtils.setField(
                    run, "startedAt", OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
            );
            return run;
        });
        RecommendationRunStartService service = new RecommendationRunStartService(
                userRepository,
                runRepository,
                policyRepository,
                gradeRuleRepository,
                skillRepository,
                jobRoleRepository,
                userSkillProvider,
                new RecommendationSnapshotFactory(new ObjectMapper())
        );

        RecommendationRunContext result = service.start(new RecommendationCreateRequest(
                2L,
                8L,
                List.of(239L, 227L, 239L)
        ));

        assertEquals(10L, result.recommendationRunId());
        assertEquals(List.of(227L, 239L), result.jobRoleIds());
        assertEquals(List.of(10L, 20L), result.userSkills().stream()
                .map(UserSkillData::skillId)
                .toList());
        assertEquals(1, result.importantSkillCount());
        assertTrue(result.userSkillSnapshotHash().matches("^[0-9a-f]{64}$"));
    }

    private JobRole role(Long id) {
        Industry industry = mock(Industry.class);
        when(industry.getId()).thenReturn(8L);
        when(industry.getIndustryName()).thenReturn("AI·개발·데이터");
        JobRole role = mock(JobRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getJobRoleName()).thenReturn("role-" + id);
        when(role.getIndustry()).thenReturn(industry);
        return role;
    }

    private Skill skill(Long id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        return skill;
    }
}

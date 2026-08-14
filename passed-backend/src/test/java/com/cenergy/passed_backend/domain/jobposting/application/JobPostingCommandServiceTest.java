package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSkillCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.CompanyRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingSkillRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.entity.UserRole;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingCommandServiceTest {
    private final JobPostingRepository postingRepository = mock(JobPostingRepository.class);
    private final JobPostingSkillRepository postingSkillRepository = mock(
            JobPostingSkillRepository.class
    );
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final JobRoleRepository jobRoleRepository = mock(JobRoleRepository.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private JobPostingCommandService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingCommandService(
                postingRepository,
                postingSkillRepository,
                companyRepository,
                jobRoleRepository,
                skillRepository,
                currentUserIdProvider,
                userRepository
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesPostingAndRequiredAndPreferredSkillsTogether() {
        Company company = mock(Company.class);
        JobRole role = mock(JobRole.class);
        Skill requiredSkill = skill(10L);
        Skill preferredSkill = skill(20L);
        JobPosting savedPosting = mock(JobPosting.class);
        User recruiter = mock(User.class);
        when(recruiter.getRole()).thenReturn(UserRole.RECRUITER);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(recruiter));
        when(savedPosting.getId()).thenReturn(100L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(jobRoleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(skillRepository.findAllByIdIn(any())).thenReturn(List.of(requiredSkill, preferredSkill));
        when(postingRepository.save(any(JobPosting.class))).thenReturn(savedPosting);

        var result = service.create(request(
                List.of(new JobPostingSkillCreateRequest(10L, (short) 3)),
                List.of(new JobPostingSkillCreateRequest(20L, (short) 2))
        ));

        assertEquals(100L, result.jobPostingId());
        assertEquals(1, result.requiredSkillCount());
        assertEquals(1, result.preferredSkillCount());
        ArgumentCaptor<Iterable<JobPostingSkill>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(postingSkillRepository).saveAll(captor.capture());
        List<JobPostingSkill> savedSkills = ((List<JobPostingSkill>) captor.getValue());
        assertEquals(List.of(JobPostingSkillType.REQUIRED, JobPostingSkillType.PREFERRED),
                savedSkills.stream().map(JobPostingSkill::getSkillType).toList());
        assertEquals(List.of((short) 3, (short) 2),
                savedSkills.stream().map(JobPostingSkill::getSkillLevel).toList());
    }

    @Test
    void rejectsSkillDuplicatedAcrossRequiredAndPreferredLists() {
        JobPostingCreateRequest request = request(
                List.of(new JobPostingSkillCreateRequest(10L, (short) 3)),
                List.of(new JobPostingSkillCreateRequest(10L, (short) 2))
        );

        assertThrows(JobPostingException.class, () -> service.create(request));

        verify(postingRepository, never()).save(any());
        verify(postingSkillRepository, never()).saveAll(any());
    }

    private JobPostingCreateRequest request(
            List<JobPostingSkillCreateRequest> required,
            List<JobPostingSkillCreateRequest> preferred
    ) {
        return new JobPostingCreateRequest(
                "백엔드 개발자", 1L, 2L, "20260812", "20260831", 1,
                "신입", "정규직", "서울", "학사", "포지션", "주요 업무",
                "자격요건", "우대사항", null, "서류 전형", required, preferred
        );
    }

    private Skill skill(Long id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        return skill;
    }
}

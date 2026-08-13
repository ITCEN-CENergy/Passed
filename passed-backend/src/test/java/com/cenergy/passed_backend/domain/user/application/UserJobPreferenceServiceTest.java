package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.IndustryRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.user.dto.UserJobPreferenceUpdateRequest;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserJobPreferenceServiceTest {
    private CurrentUserIdProvider currentUserIdProvider;
    private UserRepository userRepository;
    private IndustryRepository industryRepository;
    private JobRoleRepository jobRoleRepository;
    private UserJobPreferenceService service;

    @BeforeEach
    void setUp() {
        currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        userRepository = mock(UserRepository.class);
        industryRepository = mock(IndustryRepository.class);
        jobRoleRepository = mock(JobRoleRepository.class);
        service = new UserJobPreferenceService(
                currentUserIdProvider,
                userRepository,
                industryRepository,
                jobRoleRepository
        );
    }

    @Test
    void updatesCurrentUsersValidatedIndustryAndJobRoles() {
        Industry industry = industry(8L, "AI·개발·데이터");
        JobRole second = jobRole(239L, "AI서비스개발자", industry);
        JobRole first = jobRole(227L, "AI/ML엔지니어", industry);
        User user = mock(User.class);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-11T12:00:00+09:00");

        when(userRepository.findByIdForUpdate(257L)).thenReturn(Optional.of(user));
        when(industryRepository.findById(8L)).thenReturn(Optional.of(industry));
        when(jobRoleRepository.findAllByIdIn(List.of(239L, 227L)))
                .thenReturn(List.of(second, first));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(user.getId()).thenReturn(257L);
        when(user.getUpdatedAt()).thenReturn(updatedAt);

        var response = service.update(new UserJobPreferenceUpdateRequest(
                8L,
                List.of(239L, 227L)
        ));

        assertEquals(257L, response.userId());
        assertEquals(8L, response.industry().id());
        assertEquals(List.of(227L, 239L), response.desiredJobs().stream()
                .map(jobRole -> jobRole.id())
                .toList());
        assertEquals(updatedAt, response.updatedAt());
        verify(user).updateJobPreferences(industry, List.of(first, second));
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void returnsCurrentUsersSavedIndustryAndJobRoles() {
        Industry industry = industry(8L, "AI·개발·데이터");
        JobRole second = jobRole(239L, "AI서비스개발자", industry);
        JobRole first = jobRole(227L, "AI/ML엔지니어", industry);
        User user = mock(User.class);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-11T12:00:00+09:00");
        when(userRepository.findById(257L)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(257L);
        when(user.getDesiredIndustry()).thenReturn(industry);
        when(user.getDesiredJobRoles()).thenReturn(Set.of(second, first));
        when(user.getUpdatedAt()).thenReturn(updatedAt);

        var response = service.findCurrent();

        assertEquals(8L, response.industry().id());
        assertEquals(List.of(227L, 239L), response.desiredJobs().stream()
                .map(jobRole -> jobRole.id())
                .toList());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void rejectsJobRoleFromAnotherIndustry() {
        Industry selectedIndustry = industry(8L, "AI·개발·데이터");
        Industry otherIndustry = industry(7L, "IT·정보통신");
        JobRole mismatchedRole = jobRole(100L, "서버개발자", otherIndustry);

        when(userRepository.findByIdForUpdate(257L)).thenReturn(Optional.of(mock(User.class)));
        when(industryRepository.findById(8L)).thenReturn(Optional.of(selectedIndustry));
        when(jobRoleRepository.findAllByIdIn(List.of(100L))).thenReturn(List.of(mismatchedRole));

        UserPreferenceException exception = assertThrows(
                UserPreferenceException.class,
                () -> service.update(new UserJobPreferenceUpdateRequest(8L, List.of(100L)))
        );

        assertEquals(
                ErrorCode.USER_PREFERENCE_JOB_ROLE_INDUSTRY_MISMATCH,
                exception.getErrorCode()
        );
    }

    @Test
    void rejectsDuplicateJobRoleIds() {
        UserPreferenceException exception = assertThrows(
                UserPreferenceException.class,
                () -> service.update(new UserJobPreferenceUpdateRequest(
                        8L,
                        List.of(227L, 227L)
                ))
        );

        assertEquals(ErrorCode.USER_PREFERENCE_INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void returnsIndustryAndJobRoleCatalogsInRepositoryOrder() {
        Industry firstIndustry = industry(1L, "기획·전략");
        Industry secondIndustry = industry(8L, "AI·개발·데이터");
        Industry leakedTestIndustry = industry(99L, "concurrency-industry-123456");
        JobRole role = jobRole(227L, "AI/ML엔지니어", secondIndustry);
        when(industryRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(firstIndustry, secondIndustry, leakedTestIndustry));
        when(industryRepository.findById(8L)).thenReturn(Optional.of(secondIndustry));
        when(jobRoleRepository.findAllByIndustryIdOrderByIdAsc(8L)).thenReturn(List.of(role));

        var industries = service.findIndustries();
        var jobRoles = service.findJobRoles(8L);

        assertEquals(List.of(1L, 8L), industries.industries().stream()
                .map(industry -> industry.id())
                .toList());
        assertEquals(8L, jobRoles.industry().id());
        assertEquals(List.of(227L), jobRoles.jobRoles().stream()
                .map(jobRole -> jobRole.id())
                .toList());
    }

    private Industry industry(Long id, String name) {
        Industry industry = mock(Industry.class);
        when(industry.getId()).thenReturn(id);
        when(industry.getIndustryName()).thenReturn(name);
        return industry;
    }

    private JobRole jobRole(Long id, String name, Industry industry) {
        JobRole jobRole = mock(JobRole.class);
        when(jobRole.getId()).thenReturn(id);
        when(jobRole.getJobRoleName()).thenReturn(name);
        when(jobRole.getIndustry()).thenReturn(industry);
        return jobRole;
    }
}

package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListRequest;
import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;
import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingQueryServiceTest {
    private final JobPostingRepository repository = mock(JobPostingRepository.class);
    private final JobRecommendationRepository recommendationRepository =
            mock(JobRecommendationRepository.class);
    private final CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
    private final JobPostingQueryService service = new JobPostingQueryService(
            repository,
            recommendationRepository,
            currentUserIdProvider
    );

    @Test
    void mapsPagedJobPostingsToListResponse() {
        JobPosting posting = posting();
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        when(repository.findFiltered(
                nullable(String.class), nullable(String.class), anyLong(), anyLong(), anyBoolean(),
                nullable(CompanySize.class), anyBoolean(), anyLong(), any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(8);
            return new PageImpl<>(List.of(posting), pageable, 1);
        });
        when(recommendationRepository.findMatchedJobPostingIds(2L, List.of(100L)))
                .thenReturn(List.of(100L));

        var result = service.findAll(new JobPostingListRequest(0, 10));

        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals(100L, result.content().getFirst().jobPostingId());
        assertEquals("테스트 회사", result.content().getFirst().companyName());
        assertEquals("서버 개발", result.content().getFirst().jobRoleName());
        assertEquals("IT", result.content().getFirst().industryName());
        assertEquals(true, result.content().getFirst().matched());
    }

    @Test
    void returnsPublicPostingsWithoutMatchingDataForAnonymousUser() {
        JobPosting posting = posting();
        when(currentUserIdProvider.getCurrentUserId())
                .thenThrow(new InsufficientAuthenticationException("authentication required"));
        when(repository.findFiltered(
                eq(""), eq(""), eq(0L), eq(0L), eq(false), eq(CompanySize.STARTUP),
                eq(false), eq(0L), any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(8);
            return new PageImpl<>(List.of(posting), pageable, 1);
        });

        var result = service.findAll(new JobPostingListRequest(0, 12));

        assertEquals(1, result.content().size());
        assertEquals(false, result.content().getFirst().matched());
        verify(recommendationRepository, never()).findMatchedJobPostingIds(anyLong(), any());
    }

    @Test
    void mapsJobPostingDetail() {
        JobPosting posting = posting();
        when(repository.findById(100L)).thenReturn(Optional.of(posting));

        var result = service.findById(100L);

        assertEquals(100L, result.jobPostingId());
        assertEquals("스타트업", result.companySize());
        assertEquals("자격요건", result.qualification());
        assertEquals("복지", result.benefit());
    }

    @Test
    void rejectsMissingJobPosting() {
        when(repository.findById(100L)).thenReturn(Optional.empty());

        JobPostingException exception = assertThrows(
                JobPostingException.class,
                () -> service.findById(100L)
        );

        assertEquals(ErrorCode.JOB_POSTING_NOT_FOUND, exception.getErrorCode());
    }

    private JobPosting posting() {
        Industry industry = mock(Industry.class);
        when(industry.getIndustryName()).thenReturn("IT");
        JobRole role = mock(JobRole.class);
        when(role.getJobRoleName()).thenReturn("서버 개발");
        when(role.getIndustry()).thenReturn(industry);
        Company company = mock(Company.class);
        when(company.getCompanyName()).thenReturn("테스트 회사");
        when(company.getCompanySize()).thenReturn(CompanySize.STARTUP);
        when(company.getBenefits()).thenReturn("복지");
        JobPosting posting = mock(JobPosting.class);
        when(posting.getId()).thenReturn(100L);
        when(posting.getTitle()).thenReturn("백엔드 개발자");
        when(posting.getRegion()).thenReturn("서울");
        when(posting.getQualification()).thenReturn("자격요건");
        when(posting.getCompany()).thenReturn(company);
        when(posting.getJobRole()).thenReturn(role);
        return posting;
    }
}

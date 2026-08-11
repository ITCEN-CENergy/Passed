package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationPostingSummaryLoaderTest {

    @Test
    void loadsAllPostingContextNeededForExplanation() {
        JobPostingRepository repository = mock(JobPostingRepository.class);
        JobPosting posting = mock(JobPosting.class);
        Company company = mock(Company.class);
        when(posting.getId()).thenReturn(100L);
        when(posting.getTitle()).thenReturn("AI 서비스 개발자");
        when(posting.getCompany()).thenReturn(company);
        when(posting.getPositionDetail()).thenReturn("생성형 AI 서비스를 개발합니다.");
        when(posting.getMainDuty()).thenReturn("LLM 서비스 API 개발");
        when(posting.getQualification()).thenReturn("TypeScript 개발 역량");
        when(posting.getPreference()).thenReturn("Docker 배포 역량");
        when(company.getCompanyName()).thenReturn("테스트 회사");
        when(company.getTalentProfile()).thenReturn("주도적으로 문제를 해결하는 인재");
        when(repository.findAllByIdIn(List.of(100L))).thenReturn(List.of(posting));

        var summary = new RecommendationPostingSummaryLoader(repository)
                .load(List.of(100L))
                .get(100L);

        assertEquals("생성형 AI 서비스를 개발합니다.", summary.positionDetail());
        assertEquals("LLM 서비스 API 개발", summary.mainDuty());
        assertEquals("TypeScript 개발 역량", summary.qualification());
        assertEquals("Docker 배포 역량", summary.preference());
        assertEquals("주도적으로 문제를 해결하는 인재", summary.companyTalentProfile());
    }

    @Test
    void rejectsMissingSelectedPosting() {
        JobPostingRepository repository = mock(JobPostingRepository.class);
        when(repository.findAllByIdIn(List.of(100L))).thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> new RecommendationPostingSummaryLoader(repository).load(List.of(100L))
        );
    }
}

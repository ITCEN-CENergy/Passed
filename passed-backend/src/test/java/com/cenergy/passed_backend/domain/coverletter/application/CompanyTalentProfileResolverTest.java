package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyTalentProfileResolverTest {
    @Test
    void resolvesTrimmedTalentProfileFromLinkedPosting() {
        CoverLetterCompany coverLetter = mock(CoverLetterCompany.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company company = mock(Company.class);
        when(coverLetter.getJobPosting()).thenReturn(jobPosting);
        when(jobPosting.getCompany()).thenReturn(company);
        when(company.getTalentProfile()).thenReturn("  도전과 협업  ");

        assertThat(CompanyTalentProfileResolver.resolve(coverLetter))
                .isEqualTo("도전과 협업");
    }

    @Test
    void returnsNullForManualCoverLetterOrBlankTalentProfile() {
        CoverLetterCompany manual = mock(CoverLetterCompany.class);
        when(manual.isManual()).thenReturn(true);

        CoverLetterCompany linked = mock(CoverLetterCompany.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company company = mock(Company.class);
        when(linked.getJobPosting()).thenReturn(jobPosting);
        when(jobPosting.getCompany()).thenReturn(company);
        when(company.getTalentProfile()).thenReturn("   ");

        assertThat(CompanyTalentProfileResolver.resolve(manual)).isNull();
        assertThat(CompanyTalentProfileResolver.resolve(linked)).isNull();
    }
}

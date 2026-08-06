package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.jobposting.domain.JobPosting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobPostingDescriptionBuilderTest {

    @Test
    void buildsOrderedDescriptionAndOmitsBlankSections() {
        JobPosting posting = mock(JobPosting.class);
        when(posting.getTitle()).thenReturn("백엔드 개발자");
        when(posting.getPositionDetail()).thenReturn("플랫폼 개발");
        when(posting.getMainDuty()).thenReturn("API 개발");
        when(posting.getQualification()).thenReturn(null);
        when(posting.getPreference()).thenReturn("Spring 경험");

        String description = new JobPostingDescriptionBuilder().build(posting);

        assertThat(description).isEqualTo("""
                [공고 제목]
                백엔드 개발자

                [직무 상세]
                플랫폼 개발

                [주요 업무]
                API 개발

                [우대 사항]
                Spring 경험""");
    }
}

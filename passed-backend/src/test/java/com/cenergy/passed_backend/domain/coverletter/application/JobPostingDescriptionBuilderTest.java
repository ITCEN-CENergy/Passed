package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterManualJobPosting;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that the AI prompt builder preserves field order and omits empty job-posting fields.
 */
class JobPostingDescriptionBuilderTest {

    /** The builder includes populated sections in its documented order and skips null sections. */
    @Test
    void buildsOrderedDescriptionAndOmitsBlankSections() {
        JobPosting posting = mock(JobPosting.class);
        when(posting.getTitle()).thenReturn("Backend Developer");
        when(posting.getPositionDetail()).thenReturn("Platform development");
        when(posting.getMainDuty()).thenReturn("API development");
        when(posting.getQualification()).thenReturn(null);
        when(posting.getPreference()).thenReturn("Spring experience");

        String description = new JobPostingDescriptionBuilder().build(posting);

        assertThat(description).isEqualTo("""
                [\uACF5\uACE0 \uC81C\uBAA9]
                Backend Developer

                [\uC9C1\uBB34 \uC0C1\uC138]
                Platform development

                [\uC8FC\uC694 \uC5C5\uBB34]
                API development

                [\uC6B0\uB300 \uC0AC\uD56D]
                Spring experience""");
    }

    @Test
    void buildsDescriptionFromManualJobPosting() {
        CoverLetterManualJobPosting posting = CoverLetterManualJobPosting.create(
                "직접 입력 공고", "테스트 기업", "백엔드 개발자", null,
                "신입", "정규직", "API 개발", "Java 경험", "Spring 경험"
        );
        CoverLetterCompany coverLetter = CoverLetterCompany.createManual(
                mock(User.class), posting, "테스트 자기소개서"
        );

        String description = new JobPostingDescriptionBuilder().build(coverLetter);

        assertThat(description)
                .contains("[공고 제목]\n직접 입력 공고")
                .contains("[기업명]\n테스트 기업")
                .contains("[직무]\n백엔드 개발자")
                .contains("[주요 업무]\nAPI 개발")
                .doesNotContain("[직무 상세]");
    }
}

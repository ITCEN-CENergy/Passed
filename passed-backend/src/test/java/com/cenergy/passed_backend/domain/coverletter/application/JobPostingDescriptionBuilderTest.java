package com.cenergy.passed_backend.domain.coverletter.application;

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
}

package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a structured, nonblank description of a job posting for the feedback AI.
 * Korean headings use Unicode escapes so that the prompt remains stable across Windows encodings.
 */
@Component
public class JobPostingDescriptionBuilder {

    /** Collects only populated job-posting sections in a predictable prompt order. */
    public String build(JobPosting jobPosting) {
        List<String> sections = new ArrayList<>();
        add(sections, "\uACF5\uACE0 \uC81C\uBAA9", jobPosting.getTitle());
        add(sections, "\uC9C1\uBB34 \uC0C1\uC138", jobPosting.getPositionDetail());
        add(sections, "\uC8FC\uC694 \uC5C5\uBB34", jobPosting.getMainDuty());
        add(sections, "\uC790\uACA9 \uC694\uAC74", jobPosting.getQualification());
        add(sections, "\uC6B0\uB300 \uC0AC\uD56D", jobPosting.getPreference());
        return String.join("\n\n", sections);
    }

    /** Appends a labelled section only when its source value contains useful text. */
    private void add(List<String> sections, String heading, String value) {
        if (value != null && !value.isBlank()) {
            sections.add("[" + heading + "]\n" + value.trim());
        }
    }
}

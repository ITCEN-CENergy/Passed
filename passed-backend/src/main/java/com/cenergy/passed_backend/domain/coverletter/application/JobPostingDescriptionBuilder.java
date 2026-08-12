package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterManualJobPosting;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a structured, nonblank description of a job posting for the feedback AI.
 * Korean headings use Unicode escapes so that the prompt remains stable across Windows encodings.
 */
@Component
public class JobPostingDescriptionBuilder {

    /** 자기소개서 출처에 따라 기존 공고 또는 직접 입력 스냅샷을 AI 입력으로 조합한다. */
    public String build(CoverLetterCompany coverLetter) {
        if (!coverLetter.isManual()) {
            return build(coverLetter.getJobPosting());
        }
        CoverLetterManualJobPosting posting = coverLetter.getManualJobPosting();
        return build(
                posting.getPostingTitle(),
                posting.getPositionDetail(),
                posting.getMainDuty(),
                posting.getQualification(),
                posting.getPreference()
        );
    }

    /** Collects only populated job-posting sections in a predictable prompt order. */
    public String build(JobPosting jobPosting) {
        return build(
                jobPosting.getTitle(),
                jobPosting.getPositionDetail(),
                jobPosting.getMainDuty(),
                jobPosting.getQualification(),
                jobPosting.getPreference()
        );
    }

    /** 두 공고 출처가 동일한 필드와 순서로 AI 프롬프트를 만들도록 공통 조합 규칙을 사용한다. */
    private String build(
            String postingTitle,
            String positionDetail,
            String mainDuty,
            String qualification,
            String preference
    ) {
        List<String> sections = new ArrayList<>();
        add(sections, "\uACF5\uACE0 \uC81C\uBAA9", postingTitle);
        add(sections, "\uC9C1\uBB34 \uC0C1\uC138", positionDetail);
        add(sections, "\uC8FC\uC694 \uC5C5\uBB34", mainDuty);
        add(sections, "\uC790\uACA9 \uC694\uAC74", qualification);
        add(sections, "\uC6B0\uB300 \uC0AC\uD56D", preference);
        return String.join("\n\n", sections);
    }

    /** Appends a labelled section only when its source value contains useful text. */
    private void add(List<String> sections, String heading, String value) {
        if (value != null && !value.isBlank()) {
            sections.add("[" + heading + "]\n" + value.trim());
        }
    }
}

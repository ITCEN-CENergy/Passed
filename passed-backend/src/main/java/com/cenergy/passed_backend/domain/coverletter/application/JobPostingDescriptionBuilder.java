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
        List<String> sections = new ArrayList<>();
        add(sections, "공고 제목", posting.getPostingTitle());
        add(sections, "기업명", posting.getCompanyName());
        add(sections, "직무", posting.getJobRoleName());
        add(sections, "직무 상세", posting.getPositionDetail());
        add(sections, "경력", posting.getCareerType());
        add(sections, "고용 형태", posting.getHireType());
        add(sections, "주요 업무", posting.getMainDuty());
        add(sections, "자격 요건", posting.getQualification());
        add(sections, "우대 사항", posting.getPreference());
        return String.join("\n\n", sections);
    }

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

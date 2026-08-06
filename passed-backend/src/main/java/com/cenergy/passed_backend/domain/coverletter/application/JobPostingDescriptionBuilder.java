package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.jobposting.domain.JobPosting;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobPostingDescriptionBuilder {

    public String build(JobPosting jobPosting) {
        List<String> sections = new ArrayList<>();
        add(sections, "공고 제목", jobPosting.getTitle());
        add(sections, "직무 상세", jobPosting.getPositionDetail());
        add(sections, "주요 업무", jobPosting.getMainDuty());
        add(sections, "자격 요건", jobPosting.getQualification());
        add(sections, "우대 사항", jobPosting.getPreference());
        return String.join("\n\n", sections);
    }

    private void add(List<String> sections, String heading, String value) {
        if (value != null && !value.isBlank()) {
            sections.add("[" + heading + "]\n" + value.trim());
        }
    }
}

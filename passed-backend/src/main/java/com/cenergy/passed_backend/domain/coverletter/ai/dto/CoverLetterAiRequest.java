package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CoverLetterAiRequest(
        String question,
        String content,
        @JsonProperty("job_description") String jobDescription,
        @JsonProperty("user_skills") List<CoverLetterUserSkill> userSkills
) {
}

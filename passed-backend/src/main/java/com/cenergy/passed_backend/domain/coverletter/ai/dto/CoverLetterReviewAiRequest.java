package com.cenergy.passed_backend.domain.coverletter.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CoverLetterReviewAiRequest(
        List<Item> items,
        @JsonProperty("job_description") String jobDescription,
        @JsonProperty("company_talent_profile") String companyTalentProfile,
        @JsonProperty("user_skills") List<CoverLetterUserSkill> userSkills
) {
    public record Item(
            @JsonProperty("item_id") Long itemId,
            @JsonProperty("display_order") int displayOrder,
            String question,
            String content,
            @JsonProperty("character_limit") Integer characterLimit
    ) {
    }
}

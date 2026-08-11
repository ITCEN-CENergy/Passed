package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CommonCoverLetterResponse(
        Long coverLetterId,
        List<Item> items,
        OffsetDateTime createdAt
) {
    public static CommonCoverLetterResponse from(CoverLetter coverLetter, List<CoverLetterItem> items) {
        return new CommonCoverLetterResponse(coverLetter.getId(),
                items.stream().map(Item::from).toList(), coverLetter.getCreatedAt());
    }

    public record Item(
            Long itemId,
            Long questionId,
            CoverLetterQuestionType questionType,
            String questionText,
            String guideText,
            Integer displayOrder,
            String answer,
            BigDecimal relevanceScore
    ) {
        public static Item from(CoverLetterItem value) {
            return new Item(value.getId(), value.getQuestion().getId(),
                    value.getQuestion().getQuestionType(), value.getQuestion().getQuestionText(),
                    value.getQuestion().getGuideText(), value.getQuestion().getDisplayOrder(),
                    value.getAnswer(), value.getRelevanceScore());
        }
    }
}

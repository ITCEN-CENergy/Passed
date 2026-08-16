package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CoverLetterFeedbackService;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterItemFeedbackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공고별 자기소개서 문항에 대한 AI 피드백 생성 및 조회 API를 제공하는 컨트롤러다.
 */
@RestController
@RequestMapping("/api/v1/company-cover-letter-items")
public class CoverLetterController {
    private final CoverLetterFeedbackService feedbackService;

    public CoverLetterController(CoverLetterFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/{companyCoverLetterItemId}/feedback")
    public CoverLetterItemFeedbackResponse generateFeedback(
            @PathVariable Long companyCoverLetterItemId
    ) {
        return CoverLetterItemFeedbackResponse.from(feedbackService.generate(companyCoverLetterItemId));
    }

    @GetMapping("/{companyCoverLetterItemId}/feedback")
    public CoverLetterItemFeedbackResponse findFeedback(
            @PathVariable Long companyCoverLetterItemId
    ) {
        return CoverLetterItemFeedbackResponse.from(feedbackService.find(companyCoverLetterItemId));
    }

    @PostMapping("/{companyCoverLetterItemId}/suggested-answer")
    public CoverLetterItemFeedbackResponse generateSuggestedAnswer(
            @PathVariable Long companyCoverLetterItemId
    ) {
        return CoverLetterItemFeedbackResponse.from(
                feedbackService.generateSuggestedAnswer(companyCoverLetterItemId)
        );
    }
}

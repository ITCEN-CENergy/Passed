package com.cenergy.passed_backend.domain.coverletter.ai.client;

import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiResponse;

public interface CoverLetterAiClient {
    ValidatedCoverLetterAiResult edit(CoverLetterAiRequest request);

    CoverLetterReviewAiResponse review(CoverLetterReviewAiRequest request);
}

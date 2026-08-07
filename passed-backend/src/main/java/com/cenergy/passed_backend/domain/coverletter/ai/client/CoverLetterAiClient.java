package com.cenergy.passed_backend.domain.coverletter.ai.client;

import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;

public interface CoverLetterAiClient {
    ValidatedCoverLetterAiResult edit(CoverLetterAiRequest request);
}

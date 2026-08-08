package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationRunFailureService {
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 2000;

    private final RecommendationRunRepository runRepository;

    public RecommendationRunFailureService(RecommendationRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long recommendationRunId, Throwable throwable) {
        RecommendationRun run = runRepository.findByIdForUpdate(recommendationRunId)
                .orElseThrow(() -> new IllegalStateException("Recommendation run not found"));
        if (run.getStatus() != RecommendationRunStatus.PROCESSING) {
            return;
        }
        String message = throwable == null ? null : throwable.getMessage();
        if (message != null && message.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
        }
        run.fail(message);
    }
}

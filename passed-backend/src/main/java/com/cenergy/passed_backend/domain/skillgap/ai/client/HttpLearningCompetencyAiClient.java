package com.cenergy.passed_backend.domain.skillgap.ai.client;

import com.cenergy.passed_backend.domain.skillgap.ai.dto.LearningCompetencyAiRequest;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

public final class HttpLearningCompetencyAiClient implements LearningCompetencyAiClient {
    private static final String PATH = "/api/v1/skill-gaps/learning-competencies";

    private final RestClient restClient;

    public HttpLearningCompetencyAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId) {
        try {
            LearningCompetencyResponse response = restClient.post()
                    .uri(PATH)
                    .body(new LearningCompetencyAiRequest(userId, jobPostingId))
                    .retrieve()
                    .body(LearningCompetencyResponse.class);
            if (response == null
                    || !userId.equals(response.userId())
                    || !jobPostingId.equals(response.jobPostingId())
                    || response.competencies() == null) {
                throw new LearningCompetencyAiException(
                        ErrorCode.SKILL_GAP_INVALID_RESPONSE,
                        "learning competency AI returned an invalid response"
                );
            }
            return response;
        } catch (LearningCompetencyAiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw transportException(exception);
        } catch (RestClientResponseException exception) {
            ErrorCode code = exception.getStatusCode() == HttpStatus.NOT_FOUND
                    ? ErrorCode.SKILL_GAP_NOT_FOUND
                    : ErrorCode.SKILL_GAP_AI_UNAVAILABLE;
            throw new LearningCompetencyAiException(
                    code,
                    "learning competency AI returned an unsuccessful status",
                    exception
            );
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new LearningCompetencyAiException(
                    ErrorCode.SKILL_GAP_INVALID_RESPONSE,
                    "learning competency AI response could not be decoded",
                    exception
            );
        }
    }

    private LearningCompetencyAiException transportException(ResourceAccessException exception) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        boolean timeout = root instanceof SocketTimeoutException
                || root instanceof HttpTimeoutException
                || root instanceof HttpConnectTimeoutException;
        ErrorCode code = timeout
                ? ErrorCode.SKILL_GAP_AI_TIMEOUT
                : ErrorCode.SKILL_GAP_AI_UNAVAILABLE;
        String message = root instanceof ConnectException
                ? "learning competency AI is unavailable"
                : "learning competency AI request failed";
        return new LearningCompetencyAiException(code, message, exception);
    }
}

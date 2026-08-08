package com.cenergy.passed_backend.domain.skill.ai.client;

import com.cenergy.passed_backend.domain.skill.ai.dto.UserSkillAiRequest;
import com.cenergy.passed_backend.domain.skill.ai.dto.UserSkillAiResponse;
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

public final class HttpUserSkillAiClient implements UserSkillAiClient {
    private static final String EXTRACTION_PATH = "/api/v1/user-skills/extractions";

    private final RestClient restClient;

    public HttpUserSkillAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserSkillAiResponse extract(Long userId) {
        try {
            UserSkillAiResponse response = restClient.post()
                    .uri(EXTRACTION_PATH)
                    .body(new UserSkillAiRequest(userId))
                    .retrieve()
                    .body(UserSkillAiResponse.class);
            return validate(userId, response);
        } catch (UserSkillAiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw resourceAccessException(exception);
        } catch (RestClientResponseException exception) {
            throw responseException(exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new UserSkillAiException(
                    ErrorCode.USER_SKILL_AI_INVALID_RESPONSE,
                    "user skill AI response could not be decoded",
                    exception
            );
        }
    }

    private UserSkillAiResponse validate(Long requestedUserId, UserSkillAiResponse response) {
        if (response == null
                || !requestedUserId.equals(response.userId())
                || !Boolean.TRUE.equals(response.persisted())
                || negative(response.processedChunkCount())
                || negative(response.skillCount())
                || negative(response.unmappedCount())
                || negative(response.resumeChunksEmbedded())
                || negative(response.coverLetterChunksEmbedded())) {
            throw new UserSkillAiException(
                    ErrorCode.USER_SKILL_AI_INVALID_RESPONSE,
                    "user skill AI returned an invalid response"
            );
        }
        return response;
    }

    private boolean negative(Integer value) {
        return value == null || value < 0;
    }

    private UserSkillAiException responseException(RestClientResponseException exception) {
        ErrorCode code;
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            code = ErrorCode.USER_SKILL_NOT_FOUND;
        } else if (exception.getStatusCode().is4xxClientError()) {
            code = ErrorCode.USER_SKILL_INVALID_REQUEST;
        } else {
            code = ErrorCode.USER_SKILL_AI_UNAVAILABLE;
        }
        return new UserSkillAiException(code, "user skill AI returned an unsuccessful status", exception);
    }

    private UserSkillAiException resourceAccessException(ResourceAccessException exception) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        boolean timeout = root instanceof SocketTimeoutException
                || root instanceof HttpTimeoutException
                || root instanceof HttpConnectTimeoutException;
        if (timeout) {
            return new UserSkillAiException(
                    ErrorCode.USER_SKILL_AI_TIMEOUT,
                    "user skill analysis timed out",
                    exception
            );
        }
        if (root instanceof ConnectException) {
            return new UserSkillAiException(
                    ErrorCode.USER_SKILL_AI_UNAVAILABLE,
                    "user skill AI is unavailable",
                    exception
            );
        }
        return new UserSkillAiException(
                ErrorCode.USER_SKILL_AI_UNAVAILABLE,
                "user skill AI request failed",
                exception
        );
    }
}

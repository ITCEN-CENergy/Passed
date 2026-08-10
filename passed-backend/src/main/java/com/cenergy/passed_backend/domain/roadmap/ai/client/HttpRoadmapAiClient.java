package com.cenergy.passed_backend.domain.roadmap.ai.client;

import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiResponse;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiResponse;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.validation.RoadmapAiResponseValidator;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

public final class HttpRoadmapAiClient implements RoadmapAiClient {
    private static final String GENERATE_PATH = "/api/v1/roadmaps/generate";
    private static final String REPLAN_PATH = "/api/v1/roadmaps/replan";

    private final RestClient restClient;
    private final RoadmapAiResponseValidator validator;

    public HttpRoadmapAiClient(RestClient restClient, RoadmapAiResponseValidator validator) {
        this.restClient = restClient;
        this.validator = validator;
    }

    @Override
    public ValidatedRoadmapAiResult generate(RoadmapAiRequest request) {
        try {
            RoadmapAiResponse response = restClient.post()
                    .uri(GENERATE_PATH)
                    .body(request)
                    .retrieve()
                    .body(RoadmapAiResponse.class);
            return validator.validate(request, response);
        } catch (RoadmapAiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw resourceAccessException(exception);
        } catch (RestClientResponseException exception) {
            ErrorCode code = exception.getStatusCode().is5xxServerError()
                    ? ErrorCode.ROADMAP_AI_UNAVAILABLE
                    : ErrorCode.ROADMAP_AI_INVALID_RESPONSE;
            throw new RoadmapAiException(code, "roadmap AI returned an unsuccessful status", exception);
        } catch (HttpMessageConversionException exception) {
            throw new RoadmapAiException(
                    ErrorCode.ROADMAP_AI_INVALID_RESPONSE,
                    "roadmap AI response could not be decoded",
                    exception
            );
        } catch (RestClientException exception) {
            throw new RoadmapAiException(
                    ErrorCode.ROADMAP_AI_INVALID_RESPONSE,
                    "roadmap AI response could not be decoded",
                    exception
            );
        }
    }

    @Override
    public RoadmapReplanAiResponse replan(RoadmapReplanAiRequest request) {
        try {
            RoadmapReplanAiResponse response = restClient.post()
                    .uri(REPLAN_PATH)
                    .body(request)
                    .retrieve()
                    .body(RoadmapReplanAiResponse.class);
            if (response == null) {
                throw new RoadmapAiException(ErrorCode.ROADMAP_AI_INVALID_RESPONSE,
                        "roadmap replan AI returned an empty response");
            }
            return response;
        } catch (RoadmapAiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw resourceAccessException(exception);
        } catch (RestClientResponseException exception) {
            ErrorCode code = exception.getStatusCode().is5xxServerError()
                    ? ErrorCode.ROADMAP_AI_UNAVAILABLE : ErrorCode.ROADMAP_AI_INVALID_RESPONSE;
            throw new RoadmapAiException(code, "roadmap replan AI returned an unsuccessful status", exception);
        } catch (HttpMessageConversionException exception) {
            throw new RoadmapAiException(ErrorCode.ROADMAP_AI_INVALID_RESPONSE,
                    "roadmap replan AI response could not be decoded", exception);
        } catch (RestClientException exception) {
            throw new RoadmapAiException(ErrorCode.ROADMAP_AI_INVALID_RESPONSE,
                    "roadmap replan AI response could not be decoded", exception);
        }
    }

    private RoadmapAiException resourceAccessException(ResourceAccessException exception) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        boolean timeout = root instanceof SocketTimeoutException
                || root instanceof HttpTimeoutException
                || root instanceof HttpConnectTimeoutException;
        if (timeout) {
            return new RoadmapAiException(
                    ErrorCode.ROADMAP_AI_TIMEOUT, "roadmap AI request timed out", exception
            );
        }
        if (root instanceof ConnectException) {
            return new RoadmapAiException(
                    ErrorCode.ROADMAP_AI_UNAVAILABLE, "roadmap AI is unavailable", exception
            );
        }
        return new RoadmapAiException(
                ErrorCode.ROADMAP_AI_UNAVAILABLE, "roadmap AI request failed", exception
        );
    }
}

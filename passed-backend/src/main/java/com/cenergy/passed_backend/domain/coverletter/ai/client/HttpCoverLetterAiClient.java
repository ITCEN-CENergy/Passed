package com.cenergy.passed_backend.domain.coverletter.ai.client;

import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiResponse;
import com.cenergy.passed_backend.domain.coverletter.ai.model.ValidatedCoverLetterAiResult;
import com.cenergy.passed_backend.domain.coverletter.ai.validation.CoverLetterAiResponseValidator;
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

/**
 * Spring의 RestClient를 사용하여 외부 AI 서버와 HTTP 통신을 수행하는 구현체입니다.
 * 자기소개서 내용에 대해 AI 첨삭(편집)을 요청하고, 응답을 받아 검증한 뒤 반환합니다.
 */
public final class HttpCoverLetterAiClient implements CoverLetterAiClient {
    private static final String EDIT_PATH = "/coverletter/edit";

    private final RestClient restClient;
    private final CoverLetterAiResponseValidator validator;

    public HttpCoverLetterAiClient(RestClient restClient, CoverLetterAiResponseValidator validator) {
        this.restClient = restClient;
        this.validator = validator;
    }

    @Override
    public ValidatedCoverLetterAiResult edit(CoverLetterAiRequest request) {
        try {
            CoverLetterAiResponse response = restClient.post()
                    .uri(EDIT_PATH)
                    .body(request)
                    .retrieve()
                    .body(CoverLetterAiResponse.class);
            return validator.validate(response);
        } catch (CoverLetterAiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw resourceAccessException(exception);
        } catch (RestClientResponseException exception) {
            ErrorCode code = exception.getStatusCode().is5xxServerError()
                    ? ErrorCode.COVER_LETTER_AI_UNAVAILABLE
                    : ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE;
            throw new CoverLetterAiException(code, "cover letter AI returned an unsuccessful status", exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new CoverLetterAiException(
                    ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE,
                    "cover letter AI response could not be decoded",
                    exception
            );
        }
    }

    private CoverLetterAiException resourceAccessException(ResourceAccessException exception) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        boolean timeout = root instanceof SocketTimeoutException
                || root instanceof HttpTimeoutException
                || root instanceof HttpConnectTimeoutException;
        if (timeout) {
            return new CoverLetterAiException(
                    ErrorCode.COVER_LETTER_AI_TIMEOUT,
                    "cover letter AI request timed out",
                    exception
            );
        }
        if (root instanceof ConnectException) {
            return new CoverLetterAiException(
                    ErrorCode.COVER_LETTER_AI_UNAVAILABLE,
                    "cover letter AI is unavailable",
                    exception
            );
        }
        return new CoverLetterAiException(
                ErrorCode.COVER_LETTER_AI_UNAVAILABLE,
                "cover letter AI request failed",
                exception
        );
    }
}

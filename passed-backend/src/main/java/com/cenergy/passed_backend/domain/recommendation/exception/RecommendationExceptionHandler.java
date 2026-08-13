package com.cenergy.passed_backend.domain.recommendation.exception;

import com.cenergy.passed_backend.domain.recommendation.exception.RecommendationException;
import com.cenergy.passed_backend.domain.recommendation.api.RecommendationController;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RecommendationController.class)
public class RecommendationExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            BindException.class
    })
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.RECOMMENDATION_INVALID_REQUEST,
                "Invalid recommendation request"
        );
    }

    @ExceptionHandler(RecommendationException.class)
    public ResponseEntity<ErrorResponse> recommendation(RecommendationException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case ErrorCode.RECOMMENDATION_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case ErrorCode.RECOMMENDATION_USER_NOT_FOUND, ErrorCode.RECOMMENDATION_USER_SKILLS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCode.RECOMMENDATION_RUN_NOT_FOUND, ErrorCode.RECOMMENDATION_RESULT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCode.RECOMMENDATION_ALREADY_PROCESSING -> HttpStatus.CONFLICT;
            case ErrorCode.RECOMMENDATION_POLICY_NOT_FOUND -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

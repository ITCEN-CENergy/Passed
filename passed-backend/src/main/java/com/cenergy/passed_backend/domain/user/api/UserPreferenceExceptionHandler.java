package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.UserPreferenceException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserJobPreferenceController.class)
public class UserPreferenceExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.USER_PREFERENCE_INVALID_REQUEST,
                "Invalid user job preference request"
        );
    }

    @ExceptionHandler(UserPreferenceException.class)
    public ResponseEntity<ErrorResponse> userPreference(UserPreferenceException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case USER_PREFERENCE_INVALID_REQUEST,
                    USER_PREFERENCE_JOB_ROLE_INDUSTRY_MISMATCH -> HttpStatus.BAD_REQUEST;
            case USER_PREFERENCE_USER_NOT_FOUND,
                    USER_PREFERENCE_INDUSTRY_NOT_FOUND,
                    USER_PREFERENCE_JOB_ROLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_PREFERENCE_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflict(PessimisticLockingFailureException ignored) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.USER_PREFERENCE_CONFLICT,
                "User job preferences are being changed concurrently"
        );
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

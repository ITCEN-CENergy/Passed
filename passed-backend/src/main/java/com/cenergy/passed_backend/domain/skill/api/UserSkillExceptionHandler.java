package com.cenergy.passed_backend.domain.skill.api;

import com.cenergy.passed_backend.domain.skill.application.UserSkillException;
import com.cenergy.passed_backend.domain.skill.ai.client.UserSkillAiException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        UserSkillController.class,
        UserSkillExtractionController.class
})
public class UserSkillExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.USER_SKILL_INVALID_REQUEST,
                "Invalid user skill request"
        );
    }

    @ExceptionHandler(UserSkillException.class)
    public ResponseEntity<ErrorResponse> userSkill(UserSkillException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case USER_SKILL_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case USER_SKILL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_SKILL_INSUFFICIENT -> HttpStatus.UNPROCESSABLE_CONTENT;
            case USER_SKILL_PREFERENCE_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(UserSkillAiException.class)
    public ResponseEntity<ErrorResponse> userSkillAi(UserSkillAiException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case USER_SKILL_AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case USER_SKILL_AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case USER_SKILL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_SKILL_INVALID_REQUEST -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflict(PessimisticLockingFailureException ignored) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.USER_SKILL_PREFERENCE_CONFLICT,
                "User skills are being changed concurrently"
        );
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

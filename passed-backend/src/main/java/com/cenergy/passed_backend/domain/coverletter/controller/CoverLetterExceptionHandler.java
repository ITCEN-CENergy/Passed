package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.application.CoverLetterException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CoverLetterController.class)
public class CoverLetterExceptionHandler {

    @ExceptionHandler(CoverLetterException.class)
    public ResponseEntity<ErrorResponse> coverLetter(CoverLetterException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case COVER_LETTER_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case COVER_LETTER_ITEM_NOT_FOUND, COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case COVER_LETTER_ITEM_ANSWER_REQUIRED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case COVER_LETTER_ITEM_CHANGED, COVER_LETTER_FEEDBACK_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(CoverLetterAiException.class)
    public ResponseEntity<ErrorResponse> coverLetterAi(CoverLetterAiException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case COVER_LETTER_AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case COVER_LETTER_AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return response(status, exception.getErrorCode(), "자기소개서 첨삭 서비스 처리에 실패했습니다.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(RuntimeException ignored) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.COVER_LETTER_FEEDBACK_CONFLICT,
                "자기소개서 피드백이 동시에 변경되었습니다."
        );
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

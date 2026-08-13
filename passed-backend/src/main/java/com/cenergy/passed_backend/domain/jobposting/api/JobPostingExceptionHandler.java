package com.cenergy.passed_backend.domain.jobposting.api;

import com.cenergy.passed_backend.domain.jobposting.application.JobPostingException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = JobPostingController.class)
public class JobPostingExceptionHandler {
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.JOB_POSTING_INVALID_REQUEST,
                "Invalid job posting request"
        );
    }

    @ExceptionHandler(JobPostingException.class)
    public ResponseEntity<ErrorResponse> jobPosting(JobPostingException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case JOB_POSTING_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case JOB_POSTING_NOT_FOUND,
                    JOB_POSTING_COMPANY_NOT_FOUND,
                    JOB_POSTING_JOB_ROLE_NOT_FOUND,
                    JOB_POSTING_SKILL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case JOB_POSTING_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException ignored) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.JOB_POSTING_CONFLICT,
                "Job posting data conflicts with existing data"
        );
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            ErrorCode code,
            String message
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

package com.cenergy.passed_backend.domain.resume.api;

import com.cenergy.passed_backend.domain.resume.application.ResumeException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = {ResumeController.class, ResumePhotoController.class})
public class ResumeExceptionHandler {
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.RESUME_INVALID_REQUEST, "Invalid resume request");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return response(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.RESUME_PHOTO_TOO_LARGE,
                "Resume photo is too large");
    }

    @ExceptionHandler(ResumeException.class)
    public ResponseEntity<ErrorResponse> resume(ResumeException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case RESUME_INVALID_REQUEST, RESUME_PHOTO_INVALID -> HttpStatus.BAD_REQUEST;
            case RESUME_PHOTO_UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case RESUME_NOT_FOUND, RESUME_USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESUME_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case RESUME_PHOTO_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case RESUME_PHOTO_STORAGE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException ignored) {
        return response(HttpStatus.CONFLICT, ErrorCode.RESUME_ALREADY_EXISTS, "Resume data conflicts");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

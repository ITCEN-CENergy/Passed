package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiException;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RoadmapController.class)
public class RoadmapExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid request");
    }

    @ExceptionHandler(RoadmapException.class)
    public ResponseEntity<ErrorResponse> roadmap(RoadmapException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case ROADMAP_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case ROADMAP_NO_COMPETENCY_TO_LEARN -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(RoadmapAiException.class)
    public ResponseEntity<ErrorResponse> roadmapAi(RoadmapAiException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case ROADMAP_AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case ROADMAP_AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return response(status, exception.getErrorCode(), "Roadmap generation service failed");
    }

    @ExceptionHandler(SkillGapException.class)
    public ResponseEntity<ErrorResponse> skillGap(SkillGapException exception) {
        HttpStatus status = exception.getErrorCode() == ErrorCode.SKILL_GAP_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return response(status, exception.getErrorCode(), "Skill gap service failed");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

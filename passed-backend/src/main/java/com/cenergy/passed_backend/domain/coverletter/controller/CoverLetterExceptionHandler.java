package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.application.CoverLetterException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 기존 첨삭 API와 공고별 자기소개서 CRUD API의 도메인 예외를 일관된 HTTP 오류로 변환한다.
 */
@RestControllerAdvice(assignableTypes = {
        CoverLetterController.class,
        CommonCoverLetterController.class, //조윤지: 새로 추가된 컨트롤러
        CoverLetterQuestionController.class, //조윤지: 새로 추가된 질문 컨트롤러
        CompanyCoverLetterController.class,
        CompanyCoverLetterItemController.class
})
public class CoverLetterExceptionHandler {

    /** Bean Validation 또는 읽을 수 없는 JSON 요청은 공통 400 오류로 변환한다. */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.COVER_LETTER_INVALID_REQUEST, "Invalid cover letter request");
    }

    /** 도메인 예외 코드를 적절한 4xx 상태로 변환한다. */
    @ExceptionHandler(CoverLetterException.class)
    public ResponseEntity<ErrorResponse> coverLetter(CoverLetterException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case COVER_LETTER_INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case COVER_LETTER_NOT_FOUND,
                    COVER_LETTER_ITEM_NOT_FOUND,
                    COVER_LETTER_ITEM_FEEDBACK_NOT_FOUND,
                    COVER_LETTER_USER_NOT_FOUND,
                    COVER_LETTER_JOB_POSTING_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case COVER_LETTER_ITEM_ANSWER_REQUIRED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case COVER_LETTER_ITEM_CHANGED,
                    COVER_LETTER_FEEDBACK_CONFLICT,
                    COVER_LETTER_ALREADY_EXISTS,
                    COVER_LETTER_DISPLAY_ORDER_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, exception.getErrorCode(), exception.getMessage());
    }

    /** 외부 첨삭 AI 호출 실패를 게이트웨이 계열 오류로 변환한다. */
    @ExceptionHandler(CoverLetterAiException.class)
    public ResponseEntity<ErrorResponse> coverLetterAi(CoverLetterAiException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case COVER_LETTER_AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case COVER_LETTER_AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return response(status, exception.getErrorCode(), "자기소개서 첨삭 서비스 처리에 실패했습니다.");
    }

    /** DB 유일 제약 또는 비관적 잠금 충돌을 409 오류로 변환한다. */
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(RuntimeException ignored) {
        return response(
                HttpStatus.CONFLICT,
                ErrorCode.COVER_LETTER_FEEDBACK_CONFLICT,
                "자기소개서 피드백이 동시에 변경되었습니다."
        );
    }

    /** 오류 본문 형식을 모든 커버레터 API에서 동일하게 만든다. */
    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    /** 클라이언트가 상태 코드와 함께 읽을 수 있는 오류 본문이다. */
    public record ErrorResponse(ErrorCode code, String message) {
    }
}

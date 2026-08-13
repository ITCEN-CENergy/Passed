package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.MyPageException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MyPageController.class)
public class MyPageExceptionHandler {

    @ExceptionHandler(MyPageException.class)
    public ResponseEntity<ErrorResponse> myPage(MyPageException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getErrorCode(), exception.getMessage()));
    }

    public record ErrorResponse(ErrorCode code, String message) {
    }
}

package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CommonCoverLetterService;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterQuestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cover-letter-questions")
public class CoverLetterQuestionController {
    private final CommonCoverLetterService service;

    @GetMapping
    public ResponseEntity<List<CoverLetterQuestionResponse>> findActiveQuestions() {
        return ResponseEntity.ok(service.findActiveQuestions());
    }
}

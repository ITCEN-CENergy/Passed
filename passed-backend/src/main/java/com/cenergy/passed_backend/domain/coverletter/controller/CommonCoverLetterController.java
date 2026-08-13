package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CommonCoverLetterService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CommonCoverLetterUpsertRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CommonCoverLetterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cover-letters")
public class CommonCoverLetterController {
    private final CommonCoverLetterService service;

    @PostMapping
    public ResponseEntity<CommonCoverLetterResponse> create(
            @Valid @RequestBody CommonCoverLetterUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<CommonCoverLetterResponse> findMine() {
        return ResponseEntity.ok(service.findMine());
    }

    @PutMapping
    public ResponseEntity<CommonCoverLetterResponse> updateMine(
            @Valid @RequestBody CommonCoverLetterUpsertRequest request
    ) {
        return ResponseEntity.ok(service.update(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMine() {
        service.deleteMine();
        return ResponseEntity.noContent().build();
    }
}

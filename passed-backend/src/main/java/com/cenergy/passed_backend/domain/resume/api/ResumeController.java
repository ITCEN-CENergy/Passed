package com.cenergy.passed_backend.domain.resume.api;

import com.cenergy.passed_backend.domain.resume.application.ResumeService;
import com.cenergy.passed_backend.domain.resume.dto.ResumeResponse;
import com.cenergy.passed_backend.domain.resume.dto.ResumeUpsertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    @PostMapping
    public ResponseEntity<ResumeResponse> create(@Valid @RequestBody ResumeUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.create(request));
    }

    @GetMapping("/me")
    public ResponseEntity<ResumeResponse> findMine() {
        return ResponseEntity.ok(resumeService.findMine());
    }

    @PutMapping("/me")
    public ResponseEntity<ResumeResponse> updateMine(
            @Valid @RequestBody ResumeUpsertRequest request
    ) {
        return ResponseEntity.ok(resumeService.update(request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMine() {
        resumeService.deleteMine();
        return ResponseEntity.noContent().build();
    }
}

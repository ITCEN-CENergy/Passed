package com.cenergy.passed_backend.domain.resume.api;

import com.cenergy.passed_backend.domain.resume.application.ResumePhotoStorageService;
import com.cenergy.passed_backend.domain.resume.dto.ResumePhotoUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files/resume-photos")
public class ResumePhotoController {
    private final ResumePhotoStorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumePhotoUploadResponse> upload(
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storageService.store(file));
    }
}

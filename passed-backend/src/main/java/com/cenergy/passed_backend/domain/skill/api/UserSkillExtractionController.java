package com.cenergy.passed_backend.domain.skill.api;

import com.cenergy.passed_backend.domain.skill.application.UserSkillExtractionService;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillExtractionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/skill-extractions")
public class UserSkillExtractionController {
    private final UserSkillExtractionService extractionService;

    @PostMapping
    public ResponseEntity<UserSkillExtractionResponse> extract() {
        return ResponseEntity.ok(extractionService.extract());
    }
}

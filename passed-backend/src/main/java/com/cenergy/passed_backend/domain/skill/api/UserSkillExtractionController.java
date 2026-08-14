package com.cenergy.passed_backend.domain.skill.api;

import com.cenergy.passed_backend.domain.skill.application.UserSkillExtractionRunService;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillExtractionRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/skill-extractions")
public class UserSkillExtractionController {
    private final UserSkillExtractionRunService extractionService;

    @PostMapping
    public ResponseEntity<UserSkillExtractionRunResponse> extract() {
        return ResponseEntity.accepted().body(extractionService.start());
    }

    @GetMapping("/{extractionId}")
    public ResponseEntity<UserSkillExtractionRunResponse> get(
            @PathVariable Long extractionId
    ) {
        return ResponseEntity.ok(extractionService.findMine(extractionId));
    }
}

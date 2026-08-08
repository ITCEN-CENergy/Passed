package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/milestones")
public class MilestoneController {
    private final LearningProgressService learningProgressService;

    public MilestoneController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @PatchMapping("/{milestoneId}/completion")
    public ResponseEntity<MilestoneCompletionResponse> changeCompletion(
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneCompletionRequest request) {
        return ResponseEntity.ok(learningProgressService.changeCompletion(milestoneId, request));
    }
}

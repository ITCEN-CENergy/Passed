package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmaps")
public class RoadmapController {
    private final RoadmapCommandService commandService;

    public RoadmapController(RoadmapCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/generate")
    public ResponseEntity<RoadmapGenerateResponse> generate(@Valid @RequestBody RoadmapGenerateRequest request) {
        return ResponseEntity.ok(commandService.generate(request));
    }
}

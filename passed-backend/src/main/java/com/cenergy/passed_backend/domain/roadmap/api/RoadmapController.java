package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapCommandService;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapQueryService;
import com.cenergy.passed_backend.domain.roadmap.application.RoadmapReplanService;
import com.cenergy.passed_backend.domain.roadmap.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmaps")
public class RoadmapController {
    private final RoadmapCommandService commandService;
    private final RoadmapQueryService queryService;
    private final RoadmapReplanService replanService;

    public RoadmapController(RoadmapCommandService commandService, RoadmapQueryService queryService,
                             RoadmapReplanService replanService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.replanService = replanService;
    }

    @PostMapping("/generate")
    public ResponseEntity<RoadmapGenerateResponse> generate(@Valid @RequestBody RoadmapGenerateRequest request) {
        return ResponseEntity.ok(commandService.generate(request));
    }

    @GetMapping
    public ResponseEntity<RoadmapListResponse> findAll() {
        return ResponseEntity.ok(queryService.findAll());
    }

    @GetMapping("/{roadmapId}")
    public ResponseEntity<RoadmapDetailResponse> findById(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(queryService.findById(roadmapId));
    }

    @DeleteMapping("/{roadmapId}")
    public ResponseEntity<Void> delete(@PathVariable Long roadmapId) {
        commandService.delete(roadmapId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{roadmapId}/study-time")
    public ResponseEntity<RoadmapDetailResponse> updateStudyTime(
            @PathVariable Long roadmapId,
            @Valid @RequestBody RoadmapStudyTimeUpdateRequest request) {
        commandService.updateStudyTime(roadmapId, request.dailyStudyMinutes());
        return ResponseEntity.ok(queryService.findById(roadmapId));
    }

    @PostMapping("/{roadmapId}/replan/preview")
    public ResponseEntity<RoadmapReplanPreviewResponse> previewReplan(
            @PathVariable Long roadmapId,
            @Valid @RequestBody RoadmapReplanPreviewRequest request) {
        return ResponseEntity.ok(replanService.preview(roadmapId, request));
    }

    @PostMapping("/{roadmapId}/replan/apply")
    public ResponseEntity<RoadmapReplanApplyResponse> applyReplan(
            @PathVariable Long roadmapId,
            @Valid @RequestBody RoadmapReplanApplyRequest request) {
        return ResponseEntity.ok(replanService.apply(roadmapId, request));
    }
}

package com.cenergy.passed_backend.domain.recommendation.api;

import com.cenergy.passed_backend.domain.recommendation.application.RecommendationPreparationService;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationQueryService;
import com.cenergy.passed_backend.domain.recommendation.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/recommendations")
public class RecommendationController {
    private final RecommendationPreparationService preparationService;
    private final RecommendationQueryService queryService;

    public RecommendationController(
            RecommendationPreparationService preparationService,
            RecommendationQueryService queryService
    ) {
        this.preparationService = preparationService;
        this.queryService = queryService;
    }

    @PostMapping("/runs")
    public ResponseEntity<RecommendationCreateResponse> create(
            @Valid @RequestBody RecommendationCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(preparationService.prepare(request));
    }

    @GetMapping
    public ResponseEntity<RecommendationHistoryResponse> getHistory(
            @Valid @ModelAttribute RecommendationHistoryRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(queryService.getHistory(request));
    }

    @GetMapping("/{recommendationRunId}")
    public ResponseEntity<RecommendationResultResponse> getResult(
            @PathVariable Long recommendationRunId
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(queryService.getResult(recommendationRunId));
    }

    @GetMapping("/{recommendationRunId}/{jobRecommendationId}")
    public ResponseEntity<RecommendationDetailResponse> getDetail(
            @PathVariable Long recommendationRunId,
            @PathVariable Long jobRecommendationId
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(queryService.getDetail(recommendationRunId, jobRecommendationId));
    }

    @GetMapping("/{recommendationRunId}/user-skills")
    public ResponseEntity<RecommendationUserSkillsResponse> getUserSkills(
            @PathVariable Long recommendationRunId
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(queryService.getUserSkills(recommendationRunId));
    }
}

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
        return ResponseEntity.status(HttpStatus.CREATED).body(preparationService.prepare(request));
    }

    @GetMapping
    public RecommendationHistoryResponse getHistory(
            @Valid @ModelAttribute RecommendationHistoryRequest request
    ) {
        return queryService.getHistory(request);
    }

    @GetMapping("/{recommendationRunId}")
    public RecommendationResultResponse getResult(
            @PathVariable Long recommendationRunId,
            @Valid @ModelAttribute RecommendationUserRequest request
    ) {
        return queryService.getResult(recommendationRunId, request.userId());
    }

    @GetMapping("/{recommendationRunId}/{jobRecommendationId}")
    public RecommendationDetailResponse getDetail(
            @PathVariable Long recommendationRunId,
            @PathVariable Long jobRecommendationId,
            @Valid @ModelAttribute RecommendationUserRequest request
    ) {
        return queryService.getDetail(recommendationRunId, jobRecommendationId, request.userId());
    }

    @GetMapping("/{recommendationRunId}/user-skills")
    public RecommendationUserSkillsResponse getUserSkills(
            @PathVariable Long recommendationRunId,
            @Valid @ModelAttribute RecommendationUserRequest request
    ) {
        return queryService.getUserSkills(recommendationRunId, request.userId());
    }
}

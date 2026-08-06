package com.cenergy.passed_backend.domain.recommendation.api;

import com.cenergy.passed_backend.domain.recommendation.application.RecommendationPreparationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationPreparationService preparationService;

    public RecommendationController(RecommendationPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @PostMapping("/runs")
    public ResponseEntity<RecommendationPrepareResponse> prepare(
            @Valid @RequestBody RecommendationPrepareRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(preparationService.prepare(request));
    }
}

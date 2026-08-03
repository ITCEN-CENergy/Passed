package com.cenergy.passed_backend.domain.roadmap.ai.client;

import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;

public interface RoadmapAiClient {
    ValidatedRoadmapAiResult generate(RoadmapAiRequest request);
}

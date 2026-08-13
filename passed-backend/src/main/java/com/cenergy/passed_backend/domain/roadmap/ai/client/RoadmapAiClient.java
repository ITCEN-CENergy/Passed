package com.cenergy.passed_backend.domain.roadmap.ai.client;

import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiResponse;

public interface RoadmapAiClient {
    ValidatedRoadmapAiResult generate(RoadmapAiRequest request);

    RoadmapReplanAiResponse replan(RoadmapReplanAiRequest request);
}

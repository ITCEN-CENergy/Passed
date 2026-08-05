package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RoadmapCommandService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapGenerationService generationService;
    private final RoadmapPersistenceService persistenceService;

    public RoadmapCommandService(CurrentUserIdProvider currentUserIdProvider,
                                 RoadmapGenerationService generationService,
                                 RoadmapPersistenceService persistenceService) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.generationService = generationService;
        this.persistenceService = persistenceService;
    }

    public RoadmapGenerateResponse generate(RoadmapGenerateRequest request) {
        List<Long> jobPostingIds = normalize(request.jobPostingIds());
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid current user");
        }
        RoadmapGenerationResult result = generationService.generate(userId, jobPostingIds);
        Long roadmapId = persistenceService.save(userId, jobPostingIds, result).getId();
        return RoadmapGenerateResponse.from(roadmapId, result);
    }

    private List<Long> normalize(List<Long> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid jobPostingIds");
        }
        List<Long> normalized = List.copyOf(new LinkedHashSet<>(values));
        if (normalized.isEmpty()) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "jobPostingIds must not be empty");
        }
        return normalized;
    }
}

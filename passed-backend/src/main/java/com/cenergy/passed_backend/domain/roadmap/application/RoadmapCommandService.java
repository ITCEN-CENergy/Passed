package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.api.RoadmapGenerateResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RoadmapCommandService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapGenerationService generationService;
    private final RoadmapPersistenceService persistenceService;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final MilestoneRepository milestoneRepository;

    public RoadmapCommandService(CurrentUserIdProvider currentUserIdProvider,
                                 RoadmapGenerationService generationService,
                                 RoadmapPersistenceService persistenceService,
                                 RoadmapRepository roadmapRepository,
                                 RoadmapMilestoneRepository roadmapMilestoneRepository,
                                 MilestoneRepository milestoneRepository) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.generationService = generationService;
        this.persistenceService = persistenceService;
        this.roadmapRepository = roadmapRepository;
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.milestoneRepository = milestoneRepository;
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

    @Transactional
    public void delete(Long roadmapId) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid roadmapId");
        }

        Long userId = currentUserId();
        Roadmap roadmap = roadmapRepository.findByIdAndUserId(roadmapId, userId)
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        List<Long> milestoneIds = roadmapMilestoneRepository.findMilestoneIdsByRoadmapId(roadmapId);

        roadmapRepository.delete(roadmap);
        roadmapRepository.flush();

        if (!milestoneIds.isEmpty()) {
            milestoneRepository.deleteUnreferencedByIdsAndUserId(milestoneIds, userId);
        }
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid current user");
        }
        return userId;
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

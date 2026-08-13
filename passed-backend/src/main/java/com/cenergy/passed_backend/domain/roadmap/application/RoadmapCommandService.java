package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapGenerateResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoadmapCommandService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapGenerationService generationService;
    private final RoadmapGenerationClaimService claimService;
    private final RoadmapPersistenceService persistenceService;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final MilestoneRepository milestoneRepository;

    public RoadmapCommandService(CurrentUserIdProvider currentUserIdProvider,
                                 RoadmapGenerationService generationService,
                                 RoadmapGenerationClaimService claimService,
                                 RoadmapPersistenceService persistenceService,
                                 RoadmapRepository roadmapRepository,
                                 RoadmapMilestoneRepository roadmapMilestoneRepository,
                                 MilestoneRepository milestoneRepository) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.generationService = generationService;
        this.claimService = claimService;
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
        RoadmapGenerationClaim claim = claimService.acquire(
                userId, generationKey(jobPostingIds), jobPostingIds);
        if (!claim.acquired()) {
            throw claim.status() == RoadmapStatus.ACTIVE
                    ? RoadmapException.duplicate(claim.roadmapId())
                    : RoadmapException.generationInProgress(claim.roadmapId());
        }

        try {
            RoadmapGenerationResult result = generationService.generate(userId, jobPostingIds);
            Long roadmapId = persistenceService.complete(claim.roadmapId(), userId, result).getId();
            return RoadmapGenerateResponse.from(roadmapId, result);
        } catch (RuntimeException generationFailure) {
            markFailedWithoutMasking(claim.roadmapId(), generationFailure);
            throw generationFailure;
        }
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
        List<Long> normalized = values.stream().distinct().sorted().toList();
        if (normalized.isEmpty()) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "jobPostingIds must not be empty");
        }
        return normalized;
    }

    private String generationKey(List<Long> normalizedJobPostingIds) {
        return normalizedJobPostingIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private void markFailedWithoutMasking(Long roadmapId, RuntimeException generationFailure) {
        try {
            claimService.markFailed(roadmapId, "Roadmap generation failed");
        } catch (RuntimeException failureUpdateException) {
            generationFailure.addSuppressed(failureUpdateException);
        }
    }
}

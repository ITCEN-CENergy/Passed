package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.dto.MilestoneCompletionRequest;
import com.cenergy.passed_backend.domain.roadmap.dto.MilestoneCompletionResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class LearningProgressService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final MilestoneRepository milestoneRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final RoadmapProgressSynchronizer progressSynchronizer;

    public LearningProgressService(CurrentUserIdProvider currentUserIdProvider,
                                   MilestoneRepository milestoneRepository,
                                   RoadmapRepository roadmapRepository,
                                   RoadmapMilestoneRepository roadmapMilestoneRepository,
                                   RoadmapProgressSynchronizer progressSynchronizer) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.milestoneRepository = milestoneRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.progressSynchronizer = progressSynchronizer;
    }

    @Transactional
    public MilestoneCompletionResponse changeCompletion(Long milestoneId,
                                                        MilestoneCompletionRequest request) {
        if (milestoneId == null || milestoneId <= 0 || request == null || request.completed() == null) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid milestoneId");
        }
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid current user");
        }

        // The roadmap is the aggregate-wide serialization point. Always acquire roadmap
        // locks in id order before the milestone lock to match replan apply's lock order.
        List<Long> roadmapIds = roadmapMilestoneRepository.findRoadmapIdsByMilestoneId(milestoneId);
        if (!roadmapIds.isEmpty()) roadmapRepository.findAllForUpdateByIdInOrderById(roadmapIds);
        if (!roadmapMilestoneRepository.findRoadmapIdsByMilestoneId(milestoneId).equals(roadmapIds)) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST,
                    "Milestone roadmap changed concurrently");
        }
        Milestone milestone = milestoneRepository.findOwnedForUpdate(milestoneId, userId)
                .orElseThrow(() -> new RoadmapException(
                        ErrorCode.MILESTONE_NOT_FOUND, "Milestone not found"));
        OffsetDateTime recordedAt = OffsetDateTime.now();
        BigDecimal previous = milestone.changeCompletion(request.completed(), recordedAt);
        BigDecimal current = milestone.getProgressRate();

        progressSynchronizer.synchronizeByMilestone(milestoneId);

        return new MilestoneCompletionResponse(milestoneId, request.completed(), previous, current,
                milestone.getStatus(), milestone.getCompletedAt());
    }
}

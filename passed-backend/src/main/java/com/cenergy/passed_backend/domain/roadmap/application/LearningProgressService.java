package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.MilestoneCompletionRequest;
import com.cenergy.passed_backend.domain.roadmap.api.MilestoneCompletionResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.LearningProgress;
import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.repository.LearningProgressRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class LearningProgressService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final MilestoneRepository milestoneRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final RoadmapProgressSynchronizer progressSynchronizer;

    public LearningProgressService(CurrentUserIdProvider currentUserIdProvider,
                                   MilestoneRepository milestoneRepository,
                                   LearningProgressRepository learningProgressRepository,
                                   RoadmapProgressSynchronizer progressSynchronizer) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.milestoneRepository = milestoneRepository;
        this.learningProgressRepository = learningProgressRepository;
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

        Milestone milestone = milestoneRepository.findOwnedForUpdate(milestoneId, userId)
                .orElseThrow(() -> new RoadmapException(
                        ErrorCode.MILESTONE_NOT_FOUND, "Milestone not found"));
        OffsetDateTime recordedAt = OffsetDateTime.now();
        BigDecimal previous = milestone.changeCompletion(request.completed(), recordedAt);
        BigDecimal current = milestone.getProgressRate();

        learningProgressRepository.save(LearningProgress.record(
                milestone, previous, current, request.studiedMinutes(), request.note(), recordedAt));
        progressSynchronizer.synchronizeByMilestone(milestoneId);

        return new MilestoneCompletionResponse(milestoneId, request.completed(), previous, current,
                milestone.getStatus(), milestone.getCompletedAt());
    }
}

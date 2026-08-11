package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapJobPosting;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapJobPostingRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoadmapGenerationClaimService {
    private static final List<RoadmapStatus> BLOCKING_STATUSES =
            List.of(RoadmapStatus.CREATING, RoadmapStatus.ACTIVE);

    private final JdbcTemplate jdbcTemplate;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapJobPostingRepository jobPostingRepository;

    public RoadmapGenerationClaimService(JdbcTemplate jdbcTemplate,
                                         RoadmapRepository roadmapRepository,
                                         RoadmapJobPostingRepository jobPostingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.roadmapRepository = roadmapRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    @Transactional
    public RoadmapGenerationClaim acquire(Long userId, String generationKey, List<Long> jobPostingIds) {
        List<Long> insertedIds = jdbcTemplate.queryForList("""
                insert into roadmaps (
                    user_id, generation_key, status, total_estimated_minutes, progress_rate
                ) values (?, ?, 'CREATING', 0, 0)
                on conflict (user_id, generation_key)
                    where generation_key is not null and status in ('CREATING', 'ACTIVE')
                do nothing
                returning id
                """, Long.class, userId, generationKey);

        if (!insertedIds.isEmpty()) {
            Long roadmapId = insertedIds.getFirst();
            Roadmap roadmap = roadmapRepository.getReferenceById(roadmapId);
            jobPostingRepository.saveAll(jobPostingIds.stream()
                    .map(jobPostingId -> RoadmapJobPosting.create(roadmap, jobPostingId, null))
                    .toList());
            return RoadmapGenerationClaim.acquired(roadmapId);
        }

        Roadmap existing = roadmapRepository
                .findFirstByUserIdAndGenerationKeyAndStatusInOrderByIdAsc(
                        userId, generationKey, BLOCKING_STATUSES)
                .orElseThrow(() -> new RoadmapException(
                        ErrorCode.ROADMAP_GENERATION_CONFLICT,
                        "Roadmap generation claim could not be resolved"));
        return RoadmapGenerationClaim.existing(existing.getId(), existing.getStatus());
    }

    @Transactional
    public void markFailed(Long roadmapId, String safeFailureReason) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        if (roadmap.getStatus() == RoadmapStatus.CREATING) {
            roadmap.fail(safeFailureReason);
        }
    }
}

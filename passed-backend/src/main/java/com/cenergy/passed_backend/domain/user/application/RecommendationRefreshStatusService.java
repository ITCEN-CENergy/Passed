package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.recommendation.application.RecommendationSnapshotFactory;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RecommendationRefreshStatusService {
    private final RecommendationRunRepository recommendationRunRepository;
    private final UserSkillProvider userSkillProvider;
    private final RecommendationSnapshotFactory snapshotFactory;
    private final JdbcTemplate jdbcTemplate;

    public RecommendationRefreshStatusService(
            RecommendationRunRepository recommendationRunRepository,
            UserSkillProvider userSkillProvider,
            RecommendationSnapshotFactory snapshotFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.userSkillProvider = userSkillProvider;
        this.snapshotFactory = snapshotFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isRefreshRequired(
            Long userId,
            OffsetDateTime resumeUpdatedAt,
            OffsetDateTime coverLetterUpdatedAt
    ) {
        var latestRun = recommendationRunRepository.findLatestCompletedPreferenceRun(userId);
        if (latestRun.isEmpty()) return false;

        var currentSkills = userSkillProvider.findByUserId(userId);
        String currentSkillHash = snapshotFactory
                .createUserSkillSnapshot(currentSkills)
                .userSkillSnapshotHash();
        if (!currentSkillHash.equals(latestRun.get().getUserSkillSnapshotHash())) return true;

        OffsetDateTime latestDocumentUpdate = latest(resumeUpdatedAt, coverLetterUpdatedAt);
        if (latestDocumentUpdate == null) return false;
        List<OffsetDateTime> analyzedAt = jdbcTemplate.query(
                "select updated_at from user_skill_analysis_states where user_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("updated_at", OffsetDateTime.class),
                userId
        );
        return analyzedAt.isEmpty() || latestDocumentUpdate.isAfter(analyzedAt.getFirst());
    }

    private OffsetDateTime latest(OffsetDateTime first, OffsetDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }
}

package com.cenergy.passed_backend.recommendation.entity;

import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Entity
@Table(
        name = "recommendation_runs",
        indexes = {
                @Index(name = "idx_rec_runs_user_id", columnList = "user_id"),
                @Index(name = "idx_rec_runs_status", columnList = "status"),
                @Index(name = "idx_rec_runs_user_started_at", columnList = "user_id, started_at"),
                @Index(
                        name = "idx_rec_runs_user_skill_snapshot_hash",
                        columnList = "user_id, user_skill_snapshot_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RecommendationRunStatus status = RecommendationRunStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scoring_policy_id", nullable = false)
    private RecommendationScoringPolicy scoringPolicy;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "user_skill_snapshot_hash",
            length = 64,
            nullable = false,
            columnDefinition = "char(64)"
    )
    private String userSkillSnapshotHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_skill_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> userSkillSnapshot = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preference_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> preferenceSnapshot = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;
}

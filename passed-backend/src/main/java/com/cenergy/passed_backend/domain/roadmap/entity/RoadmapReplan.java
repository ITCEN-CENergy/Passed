package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "roadmap_replans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadmapReplan extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private UUID token;
    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoadmapReplanStatus status;
    @Column(nullable = false, columnDefinition = "text")
    private String summary;
    @Column(name = "decisions_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode decisionsJson;
    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    public static RoadmapReplan ready(Long roadmapId, Long userId, String summary, JsonNode decisionsJson) {
        RoadmapReplan value = new RoadmapReplan();
        value.token = UUID.randomUUID();
        value.roadmapId = roadmapId;
        value.userId = userId;
        value.status = RoadmapReplanStatus.READY;
        value.summary = summary;
        value.decisionsJson = decisionsJson;
        return value;
    }

    public void markApplied(OffsetDateTime appliedAt) {
        if (status != RoadmapReplanStatus.READY) {
            throw new IllegalStateException("이미 적용된 재계획입니다.");
        }
        status = RoadmapReplanStatus.APPLIED;
        this.appliedAt = appliedAt;
    }
}

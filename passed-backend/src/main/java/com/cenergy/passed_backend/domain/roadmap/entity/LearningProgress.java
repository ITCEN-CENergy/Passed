package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "learning_progresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProgress extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Column(name = "previous_progress", precision = 5, scale = 2)
    private BigDecimal previousProgress;

    @Column(name = "current_progress", precision = 5, scale = 2)
    private BigDecimal currentProgress;

    @Column(name = "studied_minutes")
    private Integer studiedMinutes;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

}

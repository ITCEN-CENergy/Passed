package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "roadmaps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Roadmap extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "title", length = 255)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RoadmapStatus status = RoadmapStatus.CREATING;
    @Column(name = "total_estimated_minutes", nullable = false)
    private Integer totalEstimatedMinutes = 0;
    @Column(name = "progress_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressRate = BigDecimal.ZERO;
    @Column(name = "estimated_end_date")
    private LocalDate estimatedEndDate;
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    public static Roadmap create(Long userId) {
        Roadmap roadmap = new Roadmap();
        roadmap.userId = userId;
        return roadmap;
    }

    public void activate(String title, int totalEstimatedMinutes) {
        if (status != RoadmapStatus.CREATING) throw new IllegalStateException("CREATING roadmap만 활성화할 수 있습니다.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title은 비어 있을 수 없습니다.");
        if (totalEstimatedMinutes < 0) throw new IllegalArgumentException("totalEstimatedMinutes는 0 이상이어야 합니다.");
        this.title = title;
        this.totalEstimatedMinutes = totalEstimatedMinutes;
        this.status = RoadmapStatus.ACTIVE;
    }

    public void fail(String safeFailureReason) {
        if (safeFailureReason == null || safeFailureReason.isBlank())
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        this.failureReason = safeFailureReason;
        this.status = RoadmapStatus.FAILED;
    }

    public void updateProgressRate(BigDecimal progressRate) {
        this.progressRate = progressRate;
        if (progressRate.compareTo(BigDecimal.valueOf(100)) == 0) {
            this.status = RoadmapStatus.COMPLETED;
        } else if (this.status == RoadmapStatus.COMPLETED) {
            this.status = RoadmapStatus.ACTIVE;
        }
    }
}

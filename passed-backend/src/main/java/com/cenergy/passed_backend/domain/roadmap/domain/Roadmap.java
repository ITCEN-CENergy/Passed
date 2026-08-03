package com.cenergy.passed_backend.domain.roadmap.domain;

import com.cenergy.passed_backend.analysis.entity.AnalysisReport;
import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "roadmaps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Roadmap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private AnalysisReport report;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "total_estimated_minutes")
    private Integer totalEstimatedMinutes;

    @Column(name = "progress_rate", precision = 5, scale = 2)
    private BigDecimal progressRate;

    @Column(name = "estimated_end_date")
    private LocalDate estimatedEndDate;

    @Column(name = "last_replanned_at")
    private OffsetDateTime lastReplannedAt;
}

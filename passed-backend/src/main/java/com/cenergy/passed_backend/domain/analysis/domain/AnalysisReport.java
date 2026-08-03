package com.cenergy.passed_backend.analysis.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "analysis_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_user_posting",
                columnNames = {"user_id", "job_posting_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisReport extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "text")
    private String weaknesses;
}

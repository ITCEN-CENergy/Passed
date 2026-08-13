package com.cenergy.passed_backend.domain.jobposting.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "job_postings",
        indexes = {
                @Index(name = "idx_job_posting_end_ymd", columnList = "end_ymd"),
                @Index(name = "idx_job_posting_career_type", columnList = "career_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_role_id", nullable = false)
    private JobRole jobRole;

    @Column(name = "start_ymd", length = 8)
    private String startYmd;

    @Column(name = "end_ymd", length = 8)
    private String endYmd;

    @Column(name = "headcount")
    private Integer headcount;

    @Column(name = "career_type", length = 50)
    private String careerType;

    @Column(name = "hire_type", length = 255)
    private String hireType;

    @Column(name = "region", length = 255)
    private String region;

    @Column(name = "edu_level", length = 255)
    private String educationLevel;

    @Column(name = "position_detail", columnDefinition = "text")
    private String positionDetail;

    @Column(name = "main_duty", columnDefinition = "text")
    private String mainDuty;

    @Column(name = "qualification", columnDefinition = "text")
    private String qualification;

    @Column(name = "preference", columnDefinition = "text")
    private String preference;

    @Column(name = "disqualify_reason", columnDefinition = "text")
    private String disqualifyReason;

    @Column(name = "process", columnDefinition = "text")
    private String process;

    public static JobPosting create(
            String title,
            Company company,
            JobRole jobRole,
            String startYmd,
            String endYmd,
            Integer headcount,
            String careerType,
            String hireType,
            String region,
            String educationLevel,
            String positionDetail,
            String mainDuty,
            String qualification,
            String preference,
            String disqualifyReason,
            String process
    ) {
        JobPosting posting = new JobPosting();
        posting.title = requireText(title, "title");
        posting.company = java.util.Objects.requireNonNull(company, "company must not be null");
        posting.jobRole = java.util.Objects.requireNonNull(jobRole, "jobRole must not be null");
        posting.startYmd = startYmd;
        posting.endYmd = endYmd;
        posting.headcount = headcount;
        posting.careerType = careerType;
        posting.hireType = hireType;
        posting.region = region;
        posting.educationLevel = educationLevel;
        posting.positionDetail = positionDetail;
        posting.mainDuty = mainDuty;
        posting.qualification = qualification;
        posting.preference = preference;
        posting.disqualifyReason = disqualifyReason;
        posting.process = process;
        return posting;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

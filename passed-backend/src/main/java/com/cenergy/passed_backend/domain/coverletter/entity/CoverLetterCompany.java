package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.jobposting.domain.JobPosting;
import com.cenergy.passed_backend.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "cover_letters_company",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letters_company_user_job_posting",
                columnNames = {"user_id", "job_posting_id"}
        ),
        indexes = @Index(
                name = "idx_cover_letters_company_job_posting_id",
                columnList = "job_posting_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterCompany extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "title", length = 255, nullable = false)
    private String title;
}

package com.cenergy.passed_backend.jobposting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "job_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_role_industry_name",
                columnNames = {"industry_id", "job_role_name"}
        ),
        indexes = @Index(name = "idx_job_role_industry_id", columnList = "industry_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @Column(name = "job_role_name", length = 100, nullable = false)
    private String jobRoleName;
}

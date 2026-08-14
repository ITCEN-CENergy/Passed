package com.cenergy.passed_backend.domain.user.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Builder
@Getter
@Setter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "field", length = 100)
    private String field;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_jobs", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> desiredJobs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private UserRole role = UserRole.GENERAL_USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_industry_id")
    private Industry desiredIndustry;

    @ManyToMany
    @JoinTable(
            name = "user_desired_job_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "job_role_id")
    )
    @OrderBy("id ASC")
    private Set<JobRole> desiredJobRoles = new LinkedHashSet<>();

    public void updateJobPreferences(Industry industry, List<JobRole> jobRoles) {
        this.desiredIndustry = Objects.requireNonNull(industry, "industry must not be null");
        Objects.requireNonNull(jobRoles, "jobRoles must not be null");
        if (jobRoles.isEmpty()) {
            throw new IllegalArgumentException("jobRoles must not be empty");
        }
        if (jobRoles.stream().anyMatch(role -> !industry.getId().equals(role.getIndustry().getId()))) {
            throw new IllegalArgumentException("Every job role must belong to the selected industry");
        }

        this.desiredJobRoles.clear();
        this.desiredJobRoles.addAll(jobRoles);

        this.field = industry.getIndustryName();
        this.desiredJobs = jobRoles.stream().map(JobRole::getJobRoleName).toList();
    }

}

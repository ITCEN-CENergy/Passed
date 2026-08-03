package com.cenergy.passed_backend.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "experiences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "company_name", length = 100, nullable = false)
    private String companyName;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "start_date", length = 10)
    private String startDate;

    @Column(name = "end_date", length = 10)
    private String endDate;

    @Column(name = "is_working")
    private Boolean working;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "responsibilities", columnDefinition = "text")
    private String responsibilities;

    @Column(name = "salary", length = 50)
    private String salary;

    @Column(name = "career_desc", columnDefinition = "text")
    private String careerDescription;
}

package com.cenergy.passed_backend.domain.resume.entity;
import com.cenergy.passed_backend.domain.resume.entity.Resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "educations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "school_type", length = 50)
    private String schoolType;

    @Column(name = "school_name", length = 100, nullable = false)
    private String schoolName;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "graduation_date")
    private LocalDate graduationDate;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "is_transfer")
    private Boolean transfer;

    @Column(name = "major_name", length = 100)
    private String majorName;

    @Column(name = "gpa", precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(name = "max_gpa", precision = 4, scale = 2)
    private BigDecimal maxGpa;

    @Column(name = "other_majors", length = 200)
    private String otherMajors;

    public static Education create(Resume resume) {
        Education education = new Education();
        education.resume = resume;
        return education;
    }

    public void update(String schoolType, String schoolName, LocalDate admissionDate,
                       LocalDate graduationDate, String status, Boolean transfer,
                       String majorName, BigDecimal gpa, BigDecimal maxGpa, String otherMajors) {
        this.schoolType = schoolType;
        this.schoolName = schoolName;
        this.admissionDate = admissionDate;
        this.graduationDate = graduationDate;
        this.status = status;
        this.transfer = transfer;
        this.majorName = majorName;
        this.gpa = gpa;
        this.maxGpa = maxGpa;
        this.otherMajors = otherMajors;
    }
}

package com.cenergy.passed_backend.domain.resume.entity;
import com.cenergy.passed_backend.domain.resume.entity.Resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

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

    public static Experience create(Resume resume) {
        Experience experience = new Experience();
        experience.resume = resume;
        return experience;
    }

    public void update(String companyName, String departmentName, LocalDate startDate,
                       LocalDate endDate, Boolean working, String position,
                       String responsibilities, String salary, String careerDescription) {
        this.companyName = companyName;
        this.departmentName = departmentName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.working = working;
        this.position = position;
        this.responsibilities = responsibilities;
        this.salary = salary;
        this.careerDescription = careerDescription;
    }
}

package com.cenergy.passed_backend.domain.resume.entity;
import com.cenergy.passed_backend.domain.resume.entity.Resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "trainings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "institution", length = 100)
    private String institution;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    public static Training create(Resume resume) {
        Training training = new Training();
        training.resume = resume;
        return training;
    }

    public void update(String name, String institution, LocalDate startDate,
                       LocalDate endDate, String description) {
        this.name = name;
        this.institution = institution;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }
}

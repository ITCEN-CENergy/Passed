package com.cenergy.passed_backend.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "start_date", length = 10)
    private String startDate;

    @Column(name = "end_date", length = 10)
    private String endDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}

package com.cenergy.passed_backend.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "activities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(name = "organization", length = 100)
    private String organization;

    @Column(name = "start_date", length = 10)
    private String startDate;

    @Column(name = "end_date", length = 10)
    private String endDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}

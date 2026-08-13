package com.cenergy.passed_backend.domain.resume.entity;
import com.cenergy.passed_backend.domain.resume.entity.Resume;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    public static Activity create(Resume resume) {
        Activity activity = new Activity();
        activity.resume = resume;
        return activity;
    }

    public void update(String activityType, String organization, LocalDate startDate,
                       LocalDate endDate, String description) {
        this.activityType = activityType;
        this.organization = organization;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }
}

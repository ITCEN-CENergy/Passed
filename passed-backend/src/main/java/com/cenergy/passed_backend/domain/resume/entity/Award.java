package com.cenergy.passed_backend.domain.resume.entity;
import com.cenergy.passed_backend.domain.resume.entity.Resume;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "awards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Award {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "issuer", length = 100)
    private String issuer;

    @Column(name = "award_date")
    private LocalDate awardDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}

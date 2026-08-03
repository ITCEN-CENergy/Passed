package com.cenergy.passed_backend.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "award_date", length = 10)
    private String awardDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}

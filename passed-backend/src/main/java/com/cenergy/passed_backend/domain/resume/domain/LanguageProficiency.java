package com.cenergy.passed_backend.resume.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "language_proficiencies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageProficiency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "language_name", length = 50, nullable = false)
    private String languageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", length = 30, nullable = false)
    private ProficiencyLevel proficiencyLevel;
}

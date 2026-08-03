package com.cenergy.passed_backend.domain.roadmap.domain;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "learning_resources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningResource extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}

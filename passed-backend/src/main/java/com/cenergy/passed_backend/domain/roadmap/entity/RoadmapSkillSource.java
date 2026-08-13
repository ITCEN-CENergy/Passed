package com.cenergy.passed_backend.domain.roadmap.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "roadmap_skill_sources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadmapSkillSource extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_skill_id", nullable = false)
    private RoadmapSkill roadmapSkill;
    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;
    @Column(name = "report_id")
    private Long reportId;
    @Column(name = "standard_competency_id", nullable = false)
    private Long standardCompetencyId;
    @Column(name = "standard_competency_name", nullable = false, length = 200)
    private String standardCompetencyName;
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private CompetencyCategory category;
    @Column(name = "current_level", nullable = false)
    private Integer currentLevel;
    @Column(name = "current_evidence", columnDefinition = "text")
    private String currentEvidence;
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 50)
    private RequirementType requirementType;
    @Column(name = "target_level", nullable = false)
    private Integer targetLevel;
    @Column(name = "gap_level", nullable = false)
    private Integer gapLevel;

    public static RoadmapSkillSource create(RoadmapSkill roadmapSkill, Long jobPostingId, Long reportId,
                                            Long competencyId, String competencyName,
                                            CompetencyCategory category, int currentLevel,
                                            String currentEvidence, RequirementType requirementType,
                                            int targetLevel, int gapLevel) {
        RoadmapSkillSource value = new RoadmapSkillSource();
        value.roadmapSkill = roadmapSkill;
        value.jobPostingId = jobPostingId;
        value.reportId = reportId;
        value.standardCompetencyId = competencyId;
        value.standardCompetencyName = competencyName;
        value.category = category;
        value.currentLevel = currentLevel;
        value.currentEvidence = currentEvidence;
        value.requirementType = requirementType;
        value.targetLevel = targetLevel;
        value.gapLevel = gapLevel;
        return value;
    }
}

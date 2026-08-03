package com.cenergy.passed_backend.roadmap.entity;
import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel; import lombok.Getter; import lombok.NoArgsConstructor;
@Getter @Entity @Table(name="roadmap_job_postings") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RoadmapJobPosting extends CreatedAtEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="roadmap_id", nullable=false) private Roadmap roadmap;
 @Column(name="job_posting_id", nullable=false) private Long jobPostingId;
 @Column(name="report_id") private Long reportId;
}

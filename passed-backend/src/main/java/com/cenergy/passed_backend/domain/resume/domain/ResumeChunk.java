package com.cenergy.passed_backend.resume.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "resume_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_resume_chunk_source",
                columnNames = {"resume_id", "source_type", "source_id", "chunk_index"}
        ),
        indexes = {
                @Index(name = "idx_resume_chunk_resume_id", columnList = "resume_id"),
                @Index(name = "idx_resume_chunk_source_type", columnList = "source_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeChunk extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30, nullable = false)
    private ResumeSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_content", nullable = false, columnDefinition = "text")
    private String chunkContent;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}

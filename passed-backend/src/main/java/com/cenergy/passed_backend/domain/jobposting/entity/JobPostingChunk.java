package com.cenergy.passed_backend.domain.jobposting.entity;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingChunkSourceType;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.common.entity.EmbeddingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "job_posting_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_posting_chunk_source",
                columnNames = {"job_posting_id", "source_type", "chunk_index"}
        ),
        indexes = {
                @Index(name = "idx_job_posting_chunk_posting_id", columnList = "job_posting_id"),
                @Index(name = "idx_job_posting_chunk_source_type", columnList = "source_type"),
                @Index(name = "idx_job_posting_chunk_index", columnList = "chunk_index")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingChunk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 50, nullable = false)
    private JobPostingChunkSourceType sourceType;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_content", nullable = false, columnDefinition = "text")
    private String chunkContent;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", length = 30, nullable = false)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "embedding_updated_at")
    private OffsetDateTime embeddingUpdatedAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;
}

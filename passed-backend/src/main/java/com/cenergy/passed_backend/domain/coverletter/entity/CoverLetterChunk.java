package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItem;
import com.cenergy.passed_backend.common.entity.EmbeddingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "cover_letter_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_chunk_item_index",
                columnNames = {"cover_letter_item_id", "chunk_index"}
        ),
        indexes = @Index(
                name = "idx_cover_letter_chunks_item_id",
                columnList = "cover_letter_item_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterChunk extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cover_letter_item_id", nullable = false)
    private CoverLetterItem coverLetterItem;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_content", nullable = false, columnDefinition = "text")
    private String chunkContent;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_updated_at")
    private OffsetDateTime embeddingUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", length = 30, nullable = false)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;
}

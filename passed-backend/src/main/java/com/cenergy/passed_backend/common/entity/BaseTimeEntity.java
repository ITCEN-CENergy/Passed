package com.cenergy.passed_backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends CreatedAtEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 하위 엔티티 변경처럼 루트 엔티티의 필드가 직접 바뀌지 않는 작업도
     * 집계의 최종 수정 시각에 반영할 수 있도록 갱신 시각을 표시한다.
     */
    protected void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }
}

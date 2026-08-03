-- 채용공고 원문에서 생성한 검색·추천용 파생 청크 테이블.
-- 원본 job_postings가 삭제되면 해당 공고의 청크도 함께 삭제한다.
CREATE TABLE IF NOT EXISTS job_posting_chunks (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_posting_id    bigint        NOT NULL,
    source_type       varchar(50)   NOT NULL,
    chunk_index       int           NOT NULL DEFAULT 0,
    chunk_content     text          NOT NULL,
    use_for_matching  boolean       NOT NULL DEFAULT true,
    embedding         vector(1536),
    content_hash      varchar(64)   NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_job_posting_chunks_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT chk_chunk_index_nonneg CHECK (chunk_index >= 0)
);

-- 한 공고·소스 안에서 chunk_index를 동기화 키로 사용한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_posting_chunks_key
    ON job_posting_chunks (job_posting_id, source_type, chunk_index);

-- 공고별 조회, 소스별 집계, 해시 기반 임베딩 재사용을 위한 인덱스.
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_posting
    ON job_posting_chunks (job_posting_id);
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_source
    ON job_posting_chunks (source_type);
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_hash
    ON job_posting_chunks (content_hash);

-- 임베딩 작업자가 조회하는 조건만 대상으로 하는 부분 인덱스.
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_embed_pending
    ON job_posting_chunks (job_posting_id)
    WHERE embedding IS NULL AND chunk_content <> '' AND use_for_matching = true;

-- UPDATE 시 updated_at을 자동으로 현재 시각으로 바꾼다.
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_job_posting_chunks_updated_at ON job_posting_chunks;
CREATE TRIGGER trg_job_posting_chunks_updated_at
    BEFORE UPDATE ON job_posting_chunks
    FOR EACH ROW
    EXECUTE FUNCTION trg_set_updated_at();

-- 절차·결격사유·복리후생은 직무 매칭 벡터 검색에서 제외한다.
ALTER TABLE job_posting_chunks
    DROP CONSTRAINT IF EXISTS chk_use_for_matching_rule;
ALTER TABLE job_posting_chunks
    ADD CONSTRAINT chk_use_for_matching_rule CHECK (
        (
            source_type IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT')
            AND use_for_matching = false
        )
        OR
        (
            source_type NOT IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT')
            AND use_for_matching = true
        )
    );

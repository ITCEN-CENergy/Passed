-- 계획서 7절에 명시된 job_posting_chunks 목표 구조.
-- job_postings 는 기존 DB에 이미 존재한다고 가정하고 여기서 만들지 않는다.
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

-- (job_posting_id, source_type, chunk_index) 고유 제약
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_posting_chunks_key
    ON job_posting_chunks (job_posting_id, source_type, chunk_index);

CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_posting
    ON job_posting_chunks (job_posting_id);
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_source
    ON job_posting_chunks (source_type);
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_hash
    ON job_posting_chunks (content_hash);

-- 임베딩 대상 조회 인덱스: embedding IS NULL AND chunk_content <> '' AND use_for_matching
CREATE INDEX IF NOT EXISTS ix_job_posting_chunks_embed_pending
    ON job_posting_chunks (job_posting_id)
    WHERE embedding IS NULL AND chunk_content <> '' AND use_for_matching = true;

-- updated_at 자동 갱신 트리거
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

-- use_for_matching 규칙(DB 제약):
--   PROCESS, DISQUALIFICATION, BENEFIT -> false
--   나머지 source_type                -> true
ALTER TABLE job_posting_chunks DROP CONSTRAINT IF EXISTS chk_use_for_matching_rule;
ALTER TABLE job_posting_chunks
    ADD CONSTRAINT chk_use_for_matching_rule CHECK (
        ((source_type IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT')) AND use_for_matching = false)
        OR
        ((source_type NOT IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT')) AND use_for_matching = true)
    );

-- 이 테이블의 소유자는 passed-backend Flyway(V3)다.
-- 로컬에서 Flyway가 적용되지 않은 DB를 사용할 때만 동일 계약으로 생성한다.
CREATE TABLE IF NOT EXISTS job_posting_chunks (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_posting_id bigint NOT NULL,
    source_type varchar(50) NOT NULL,
    chunk_index int NOT NULL DEFAULT 0,
    chunk_content text NOT NULL,
    embedding vector(1536),
    embedding_model varchar(100),
    embedding_status varchar(30) NOT NULL DEFAULT 'PENDING',
    embedding_updated_at timestamptz,
    content_hash varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_job_posting_chunk_job_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_posting_chunk_source
        UNIQUE (job_posting_id, source_type, chunk_index),
    CONSTRAINT ck_job_posting_chunk_source_type CHECK (
        source_type IN (
            'POSITION_DETAIL', 'MAIN_TASK', 'REQUIREMENT', 'PREFERENCE',
            'BENEFIT', 'PROCESS', 'DISQUALIFICATION'
        )
    ),
    CONSTRAINT ck_job_posting_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_job_posting_chunk_content CHECK (btrim(chunk_content) <> ''),
    CONSTRAINT ck_job_posting_chunk_embedding_status CHECK (
        embedding_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_job_posting_chunk_content_hash CHECK (
        content_hash IS NULL OR content_hash ~ '^[0-9a-fA-F]{64}$'
    )
);

-- 구버전 파이프라인 테이블을 현재 임베딩 수명주기 계약으로 보강한다.
ALTER TABLE job_posting_chunks
    ADD COLUMN IF NOT EXISTS embedding_model varchar(100),
    ADD COLUMN IF NOT EXISTS embedding_status varchar(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS embedding_updated_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_job_posting_chunks_job_posting_id
    ON job_posting_chunks(job_posting_id);
CREATE INDEX IF NOT EXISTS idx_job_posting_chunks_source_type
    ON job_posting_chunks(source_type);
CREATE INDEX IF NOT EXISTS idx_job_posting_chunks_embedding_status
    ON job_posting_chunks(embedding_status);
CREATE INDEX IF NOT EXISTS idx_job_posting_chunks_embedding_model
    ON job_posting_chunks(embedding_model);
CREATE INDEX IF NOT EXISTS idx_job_posting_chunks_content_hash
    ON job_posting_chunks(content_hash);

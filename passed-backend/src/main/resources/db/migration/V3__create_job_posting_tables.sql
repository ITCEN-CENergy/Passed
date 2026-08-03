-- =============================================
-- 1. 산업
-- =============================================

CREATE TABLE industries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    industry_name VARCHAR(100) NOT NULL,

    CONSTRAINT uk_industries_industry_name
        UNIQUE (industry_name),

    CONSTRAINT ck_industries_industry_name
        CHECK (BTRIM(industry_name) <> '')
);


-- =============================================
-- 2. 직무
-- 하나의 산업에 여러 직무가 포함되는 구조
-- =============================================

CREATE TABLE job_roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    industry_id BIGINT NOT NULL,
    job_role_name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_job_role_industry
        FOREIGN KEY (industry_id)
        REFERENCES industries(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_job_role_industry_name
        UNIQUE (industry_id, job_role_name),

    CONSTRAINT ck_job_roles_job_role_name
        CHECK (BTRIM(job_role_name) <> '')
);


-- =============================================
-- 3. 기업
-- =============================================

CREATE TABLE companies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    company_size VARCHAR(50),
    talent_profile TEXT,
    benefits TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_companies_company_name
        UNIQUE (company_name),

    CONSTRAINT ck_companies_company_name
        CHECK (BTRIM(company_name) <> '')
);


-- =============================================
-- 4. 채용공고
-- =============================================

CREATE TABLE job_postings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    company_id BIGINT NOT NULL,
    job_role_id BIGINT NOT NULL,
    start_ymd VARCHAR(8),
    end_ymd VARCHAR(8),
    headcount INT,
    career_type VARCHAR(50),
    hire_type VARCHAR(255),
    region VARCHAR(255),
    edu_level VARCHAR(255),
    position_detail TEXT,
    main_duty TEXT,
    qualification TEXT,
    preference TEXT,
    disqualify_reason TEXT,
    process TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_posting_company
        FOREIGN KEY (company_id)
        REFERENCES companies(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_job_posting_job_role
        FOREIGN KEY (job_role_id)
        REFERENCES job_roles(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_job_postings_title
        CHECK (BTRIM(title) <> ''),

    CONSTRAINT ck_job_postings_start_ymd
        CHECK (
            start_ymd IS NULL
            OR start_ymd ~ '^[0-9]{8}$'
        ),

    CONSTRAINT ck_job_postings_end_ymd
        CHECK (
            end_ymd IS NULL
            OR end_ymd ~ '^[0-9]{8}$'
        ),

    CONSTRAINT ck_job_postings_date_order
        CHECK (
            start_ymd IS NULL
            OR end_ymd IS NULL
            OR start_ymd <= end_ymd
        ),

    CONSTRAINT ck_job_postings_headcount
        CHECK (
            headcount IS NULL
            OR headcount > 0
        )
);


-- =============================================
-- 5. 채용공고 청크
--
-- POSITION_DETAIL  : 포지션 상세
-- MAIN_TASK        : 주요 업무
-- REQUIREMENT      : 자격요건
-- PREFERENCE       : 우대사항
-- BENEFIT          : 복지 및 혜택
-- PROCESS          : 채용 절차
-- DISQUALIFICATION : 결격 사유
-- =============================================

CREATE TABLE job_posting_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_posting_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    chunk_content TEXT NOT NULL,

    -- 임베딩 관리 컬럼
    embedding VECTOR(1536),
    embedding_model VARCHAR(100),
    embedding_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    embedding_updated_at TIMESTAMPTZ,
    content_hash VARCHAR(64),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_posting_chunk_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_job_posting_chunk_source
        UNIQUE (
            job_posting_id,
            source_type,
            chunk_index
        ),

    CONSTRAINT ck_job_posting_chunk_source_type
        CHECK (
            source_type IN (
                'POSITION_DETAIL',
                'MAIN_TASK',
                'REQUIREMENT',
                'PREFERENCE',
                'BENEFIT',
                'PROCESS',
                'DISQUALIFICATION'
            )
        ),

    CONSTRAINT ck_job_posting_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT ck_job_posting_chunk_content
        CHECK (BTRIM(chunk_content) <> ''),

    CONSTRAINT ck_job_posting_chunk_embedding_status
        CHECK (
            embedding_status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_job_posting_chunk_content_hash
        CHECK (
            content_hash IS NULL
            OR content_hash ~ '^[0-9a-fA-F]{64}$'
        )
);

-- =============================================
-- 6. 일반 조회용 인덱스
-- =============================================

-- 산업별 직무 조회
CREATE INDEX idx_job_roles_industry_id
    ON job_roles(industry_id);

-- 기업 규모별 조회
CREATE INDEX idx_companies_company_size
    ON companies(company_size);

-- 기업별 채용공고 조회
CREATE INDEX idx_job_postings_company_id
    ON job_postings(company_id);

-- 직무별 채용공고 조회
CREATE INDEX idx_job_postings_job_role_id
    ON job_postings(job_role_id);

-- 마감일 기준 조회
CREATE INDEX idx_job_postings_end_ymd
    ON job_postings(end_ymd);

-- 채용구분 조회
CREATE INDEX idx_job_postings_career_type
    ON job_postings(career_type);

-- 직무와 마감일 기준 후보 조회
CREATE INDEX idx_job_postings_role_end_ymd
    ON job_postings(job_role_id, end_ymd);


-- 공고별 청크 조회
CREATE INDEX idx_job_posting_chunks_job_posting_id
    ON job_posting_chunks(job_posting_id);

-- 청크 출처 유형별 조회
CREATE INDEX idx_job_posting_chunks_source_type
    ON job_posting_chunks(source_type);

-- 청크 순서 조회
CREATE INDEX idx_job_posting_chunks_chunk_index
    ON job_posting_chunks(chunk_index);

-- 공고와 청크 출처 유형 조회
CREATE INDEX idx_job_posting_chunks_posting_source
    ON job_posting_chunks(job_posting_id, source_type);

-- 임베딩 처리 상태별 조회
CREATE INDEX idx_job_posting_chunks_embedding_status
    ON job_posting_chunks(embedding_status);

-- 임베딩 모델별 조회
CREATE INDEX idx_job_posting_chunks_embedding_model
    ON job_posting_chunks(embedding_model);

-- 동일 텍스트 및 변경 여부 확인
CREATE INDEX idx_job_posting_chunks_content_hash
    ON job_posting_chunks(content_hash);


-- =============================================
-- 7. updated_at 자동 갱신 함수
-- =============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


-- =============================================
-- 8. updated_at 자동 갱신 트리거
-- =============================================

CREATE TRIGGER trg_companies_updated_at
    BEFORE UPDATE ON companies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_job_postings_updated_at
    BEFORE UPDATE ON job_postings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_job_posting_chunks_updated_at
    BEFORE UPDATE ON job_posting_chunks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- =============================================
-- 9. pgvector HNSW 인덱스
-- 코사인 거리 기반 유사도 검색
-- =============================================

CREATE INDEX idx_job_posting_chunks_embedding_hnsw
    ON job_posting_chunks
    USING hnsw (embedding vector_cosine_ops);


COMMIT;

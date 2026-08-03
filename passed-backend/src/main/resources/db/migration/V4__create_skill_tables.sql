BEGIN;

-- =============================================
-- pgvector 확장 활성화
-- 이미 활성화된 경우에는 아무 작업도 하지 않음
-- =============================================

CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================
-- 1. 공통 스킬 마스터
--
-- TECHNICAL_SKILL  : 기술 
-- EXPERIENCE       : 경험 
-- BEHAVIORAL_TRAIT : 성향
-- CERTIFICATION    : 자격증
-- =============================================

CREATE TABLE skills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    description TEXT,
    embedding VECTOR(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_skills_name
        UNIQUE (name),

    CONSTRAINT ck_skills_name
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT ck_skills_category
        CHECK (
            category IS NULL
            OR category IN (
                'TECHNICAL_SKILL',
                'EXPERIENCE',
                'BEHAVIORAL_TRAIT',
                'CERTIFICATION'
            )
        )
);


-- =============================================
-- 2. 사용자 보유 스킬
-- 사용자와 공통 스킬 간 다대다 연결
--
-- skill_level
-- 1 : 기초
-- 2 : 활용
-- 3 : 능숙
-- =============================================

CREATE TABLE user_skills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_level SMALLINT NOT NULL DEFAULT 1,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_skill_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_skill_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_user_skill
        UNIQUE (user_id, skill_id),

    CONSTRAINT ck_user_skill_level
        CHECK (skill_level BETWEEN 1 AND 3)
);


-- =============================================
-- 3. 사용자 스킬 추출 근거
--
-- resume_chunk_id를 통해 원본 이력서 항목이나
-- 자기소개서 청크까지 추적
--
-- mapping_method
-- EXACT     : 표준 스킬명과 정확히 일치
-- KEYWORD   : 키워드 또는 별칭 규칙으로 매핑
-- EMBEDDING : 임베딩 유사도로 매핑
-- =============================================

CREATE TABLE user_skill_evidences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_skill_id BIGINT NOT NULL,
    resume_chunk_id BIGINT NOT NULL,
    extracted_name VARCHAR(100) NOT NULL,
    mapping_method VARCHAR(30) NOT NULL,
    mapping_similarity NUMERIC(4, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_skill_evidence_user_skill
        FOREIGN KEY (user_skill_id)
        REFERENCES user_skills(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_skill_evidence_resume_chunk
        FOREIGN KEY (resume_chunk_id)
        REFERENCES resume_chunks(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_user_skill_evidence_chunk
        UNIQUE (user_skill_id, resume_chunk_id),

    CONSTRAINT ck_user_skill_evidence_extracted_name
        CHECK (BTRIM(extracted_name) <> ''),

    CONSTRAINT ck_user_skill_evidence_mapping_method
        CHECK (
            mapping_method IN (
                'EXACT',
                'KEYWORD',
                'EMBEDDING'
            )
        ),

    CONSTRAINT ck_user_skill_evidence_similarity
        CHECK (
            mapping_similarity IS NULL
            OR mapping_similarity BETWEEN 0 AND 1
        )
);


-- =============================================
-- 4. 채용공고 요구 스킬
--
-- REQUIRED  : 필수 스킬
-- PREFERRED : 우대 스킬
-- RELATED   : 업무 관련 스킬
--
-- skill_level
-- 1 : 기초
-- 2 : 활용
-- 3 : 능숙
-- =============================================

CREATE TABLE job_posting_skills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_posting_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_type VARCHAR(30) NOT NULL,
    skill_level SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_posting_skill_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_job_posting_skill_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_job_posting_skill
        UNIQUE (job_posting_id, skill_id),

    CONSTRAINT ck_job_posting_skill_type
        CHECK (
            skill_type IN (
                'REQUIRED',
                'PREFERRED',
                'RELATED'
            )
        ),

    CONSTRAINT ck_job_posting_skill_level
        CHECK (skill_level BETWEEN 1 AND 3)
);


-- =============================================
-- 5. 채용공고 스킬 추출 근거
--
-- job_posting_chunk_id를 통해
-- 주요 업무, 자격요건, 우대사항 등의 원문 추적
-- =============================================

CREATE TABLE job_posting_skill_evidences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_posting_skill_id BIGINT NOT NULL,
    job_posting_chunk_id BIGINT NOT NULL,
    extracted_name VARCHAR(100) NOT NULL,
    mapping_method VARCHAR(30) NOT NULL,
    mapping_similarity NUMERIC(4, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_skill_evidence_job_skill
        FOREIGN KEY (job_posting_skill_id)
        REFERENCES job_posting_skills(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_job_skill_evidence_job_chunk
        FOREIGN KEY (job_posting_chunk_id)
        REFERENCES job_posting_chunks(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_job_skill_evidence_chunk
        UNIQUE (
            job_posting_skill_id,
            job_posting_chunk_id
        ),

    CONSTRAINT ck_job_skill_evidence_extracted_name
        CHECK (BTRIM(extracted_name) <> ''),

    CONSTRAINT ck_job_skill_evidence_mapping_method
        CHECK (
            mapping_method IN (
                'EXACT',
                'KEYWORD',
                'EMBEDDING'
            )
        ),

    CONSTRAINT ck_job_skill_evidence_similarity
        CHECK (
            mapping_similarity IS NULL
            OR mapping_similarity BETWEEN 0 AND 1
        )
);


-- =============================================
-- 6. 일반 조회용 인덱스
-- =============================================

-- 스킬 카테고리별 조회
CREATE INDEX idx_skills_category
    ON skills(category);


-- 사용자별 스킬 조회
CREATE INDEX idx_user_skills_user_id
    ON user_skills(user_id);

-- 특정 스킬 보유 사용자 조회
CREATE INDEX idx_user_skills_skill_id
    ON user_skills(skill_id);

-- 중요 스킬 조회
CREATE INDEX idx_user_skills_is_important
    ON user_skills(is_important);

-- 사용자별 중요 스킬 조회 최적화
CREATE INDEX idx_user_skills_user_important
    ON user_skills(user_id, is_important);


-- 사용자 스킬별 근거 조회
CREATE INDEX idx_user_skill_evidences_user_skill_id
    ON user_skill_evidences(user_skill_id);

-- 특정 이력서 청크에서 추출된 스킬 조회
CREATE INDEX idx_user_skill_evidences_resume_chunk_id
    ON user_skill_evidences(resume_chunk_id);

-- 매핑 방식별 조회
CREATE INDEX idx_user_skill_evidences_mapping_method
    ON user_skill_evidences(mapping_method);


-- 공고별 스킬 조회
CREATE INDEX idx_job_posting_skills_job_posting_id
    ON job_posting_skills(job_posting_id);

-- 특정 스킬을 요구하는 공고 조회
CREATE INDEX idx_job_posting_skills_skill_id
    ON job_posting_skills(skill_id);

-- 필수·우대·관련 스킬 유형 조회
CREATE INDEX idx_job_posting_skills_skill_type
    ON job_posting_skills(skill_type);

-- 공고별 필수·우대 스킬 조회 최적화
CREATE INDEX idx_job_posting_skills_posting_type
    ON job_posting_skills(job_posting_id, skill_type);


-- 공고 스킬별 근거 조회
CREATE INDEX idx_job_skill_evidences_job_skill_id
    ON job_posting_skill_evidences(job_posting_skill_id);

-- 특정 공고 청크에서 추출된 스킬 조회
CREATE INDEX idx_job_skill_evidences_job_chunk_id
    ON job_posting_skill_evidences(job_posting_chunk_id);

-- 매핑 방식별 조회
CREATE INDEX idx_job_skill_evidences_mapping_method
    ON job_posting_skill_evidences(mapping_method);


-- =============================================
-- 7. pgvector HNSW 인덱스
--
-- 추출된 스킬 후보와 공통 스킬 사전을
-- 코사인 유사도로 비교하기 위한 인덱스
-- =============================================

CREATE INDEX idx_skills_embedding_hnsw
    ON skills
    USING hnsw (embedding vector_cosine_ops);


-- =============================================
-- 8. updated_at 자동 갱신 함수
--
-- 이전 마이그레이션에서 같은 함수가 생성되어 있어도
-- CREATE OR REPLACE이므로 재사용 가능
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
-- 9. updated_at 자동 갱신 트리거
-- =============================================

CREATE TRIGGER trg_user_skills_updated_at
    BEFORE UPDATE ON user_skills
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_user_skill_evidences_updated_at
    BEFORE UPDATE ON user_skill_evidences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_job_skill_evidences_updated_at
    BEFORE UPDATE ON job_posting_skill_evidences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMIT;

-- =============================================
-- 1. 사용자
-- =============================================

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    field VARCHAR(100),
    desired_jobs JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email
        UNIQUE (email),

    CONSTRAINT ck_users_desired_jobs_array
        CHECK (
            desired_jobs IS NULL
            OR jsonb_typeof(desired_jobs) = 'array'
        )
);


-- =============================================
-- 2. 자기소개서 고정 질문 마스터
-- =============================================

CREATE TABLE cover_letter_questions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_type VARCHAR(30) NOT NULL,
    question_text VARCHAR(500) NOT NULL,
    guide_text TEXT,
    match_weight NUMERIC(3, 2),
    display_order INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_cover_letter_questions_type
        UNIQUE (question_type),

    CONSTRAINT ck_cover_letter_questions_type
        CHECK (
            question_type IN (
                'MOTIVATION',
                'PERSONALITY',
                'EXPERIENCE'
            )
        ),

    CONSTRAINT ck_cover_letter_questions_weight
        CHECK (
            match_weight IS NULL
            OR match_weight BETWEEN 0 AND 1
        ),

    CONSTRAINT ck_cover_letter_questions_order
        CHECK (
            display_order IS NULL
            OR display_order >= 0
        )
);


-- =============================================
-- 3. 자기소개서
-- 사용자와 자기소개서 1:1
-- =============================================

CREATE TABLE cover_letters (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letter_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_cover_letter_user
        UNIQUE (user_id)
);


-- =============================================
-- 4. 자기소개서 항목
-- =============================================

CREATE TABLE cover_letter_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cover_letter_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer TEXT,
    relevance_score NUMERIC(4, 2),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letter_item_cover_letter
        FOREIGN KEY (cover_letter_id)
        REFERENCES cover_letters(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cover_letter_item_question
        FOREIGN KEY (question_id)
        REFERENCES cover_letter_questions(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_cover_letter_item_question
        UNIQUE (cover_letter_id, question_id),

    CONSTRAINT ck_cover_letter_item_relevance
        CHECK (
            relevance_score IS NULL
            OR relevance_score BETWEEN 0 AND 1
        )
);


-- =============================================
-- 5. 자기소개서 청크
-- 자기소개서 항목의 답변을 검색 가능한 단위로 분할하여 저장
-- =============================================

CREATE TABLE cover_letter_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cover_letter_item_id BIGINT NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    chunk_content TEXT NOT NULL,

    -- 임베딩 관리
    embedding VECTOR(1536),
    content_hash VARCHAR(64),
    embedding_model VARCHAR(100),
    embedding_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letter_chunk_item
        FOREIGN KEY (cover_letter_item_id)
        REFERENCES cover_letter_items(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_cover_letter_chunk_item_index
        UNIQUE (cover_letter_item_id, chunk_index),

    CONSTRAINT ck_cover_letter_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT ck_cover_letter_chunk_content
        CHECK (BTRIM(chunk_content) <> ''),

    CONSTRAINT ck_cover_letter_chunk_embedding_status
        CHECK (
            embedding_status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_cover_letter_chunk_content_hash
        CHECK (
            content_hash IS NULL
            OR content_hash ~ '^[0-9a-fA-F]{64}$'
        )
);


-- =============================================
-- 6. 이력서
-- 사용자와 이력서 1:1
-- =============================================

CREATE TABLE resumes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resume_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_resume_user
        UNIQUE (user_id)
);


-- =============================================
-- 7. 이력서 청크
-- source_id는 여러 하위 테이블을 가리키는 다형 참조이므로
-- 일반적인 외래키 제약조건을 설정하지 않음
-- =============================================

CREATE TABLE resume_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    chunk_content TEXT NOT NULL,

    -- 임베딩 관리
    embedding VECTOR(1536),
    embedding_model VARCHAR(100),
    embedding_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    embedding_updated_at TIMESTAMPTZ,
    content_hash VARCHAR(64),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resume_chunk_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_resume_chunk_source
        UNIQUE (
            resume_id,
            source_type,
            source_id,
            chunk_index
        ),

    CONSTRAINT ck_resume_chunk_source_type
        CHECK (
            source_type IN (
                'EDUCATION',
                'EXPERIENCE',
                'ACTIVITY',
                'TRAINING',
                'CERTIFICATION',
                'AWARD',
                'OVERSEAS_EXPERIENCE',
                'LANGUAGE'
            )
        ),

    CONSTRAINT ck_resume_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT ck_resume_chunk_content
        CHECK (BTRIM(chunk_content) <> ''),

    CONSTRAINT ck_resume_chunk_embedding_status
        CHECK (
            embedding_status IN (
                'PENDING',
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_resume_chunk_content_hash
        CHECK (
            content_hash IS NULL
            OR content_hash ~ '^[0-9a-fA-F]{64}$'
        )
);

-- =============================================
-- 8. 개인정보
-- 이력서와 개인정보 1:1
-- =============================================

CREATE TABLE personal_infos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    birth_date DATE,
    gender VARCHAR(10),
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(255),
    photo_url VARCHAR(500),

    CONSTRAINT fk_personal_info_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_personal_info_resume
        UNIQUE (resume_id)
);


-- =============================================
-- 9. 학력
-- =============================================

CREATE TABLE educations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    school_type VARCHAR(50),
    school_name VARCHAR(100) NOT NULL,
    admission_date DATE,
    graduation_date DATE,
    status VARCHAR(50),
    is_transfer BOOLEAN,
    major_name VARCHAR(100),
    gpa NUMERIC(4, 2),
    max_gpa NUMERIC(4, 2),
    other_majors VARCHAR(200),

    CONSTRAINT fk_education_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_education_gpa
        CHECK (
            gpa IS NULL
            OR gpa >= 0
        ),

    CONSTRAINT ck_education_max_gpa
        CHECK (
            max_gpa IS NULL
            OR max_gpa > 0
        ),

    CONSTRAINT ck_education_gpa_range
        CHECK (
            gpa IS NULL
            OR max_gpa IS NULL
            OR gpa <= max_gpa
        )
);


-- =============================================
-- 10. 경력
-- =============================================

CREATE TABLE experiences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    is_working BOOLEAN,
    position VARCHAR(50),
    responsibilities TEXT,
    salary VARCHAR(50),
    career_desc TEXT,

    CONSTRAINT fk_experience_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 11. 인턴 및 대외활동
-- =============================================

CREATE TABLE activities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    activity_type VARCHAR(50),
    organization VARCHAR(100),
    start_date DATE,
    end_date DATE,
    description TEXT,

    CONSTRAINT fk_activity_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 12. 교육 및 훈련
-- =============================================

CREATE TABLE trainings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    name VARCHAR(150),
    institution VARCHAR(100),
    start_date DATE,
    end_date DATE,
    description TEXT,

    CONSTRAINT fk_training_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 13. 자격증 및 어학시험
-- =============================================

CREATE TABLE certifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    name VARCHAR(150),
    issuer VARCHAR(100),
    acquisition_date DATE,

    CONSTRAINT fk_certification_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 14. 수상경력
-- =============================================

CREATE TABLE awards (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    name VARCHAR(150),
    issuer VARCHAR(100),
    award_date DATE,
    description TEXT,

    CONSTRAINT fk_award_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 15. 해외경험
-- =============================================

CREATE TABLE overseas_experiences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    country_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    description TEXT,

    CONSTRAINT fk_overseas_experience_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE
);


-- =============================================
-- 16. 외국어 회화 능력
-- =============================================

CREATE TABLE language_proficiencies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    language_name VARCHAR(50) NOT NULL,
    proficiency_level VARCHAR(30) NOT NULL,

    CONSTRAINT fk_language_proficiency_resume
        FOREIGN KEY (resume_id)
        REFERENCES resumes(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_language_proficiency_level
        CHECK (
            proficiency_level IN (
                'DAILY',
                'BUSINESS',
                'NATIVE'
            )
        )
);


-- =============================================
-- 17. 일반 조회용 인덱스
-- PostgreSQL은 외래키 컬럼의 인덱스를 자동 생성하지 않으므로 별도 생성
-- =============================================

CREATE INDEX idx_cover_letter_items_question_id
    ON cover_letter_items(question_id);

CREATE INDEX idx_cover_letter_chunks_item_id
    ON cover_letter_chunks(cover_letter_item_id);

CREATE INDEX idx_resume_chunks_resume_id
    ON resume_chunks(resume_id);

CREATE INDEX idx_resume_chunks_source_type
    ON resume_chunks(source_type);

CREATE INDEX idx_resume_chunks_source
    ON resume_chunks(source_type, source_id);

CREATE INDEX idx_educations_resume_id
    ON educations(resume_id);

CREATE INDEX idx_experiences_resume_id
    ON experiences(resume_id);

CREATE INDEX idx_activities_resume_id
    ON activities(resume_id);

CREATE INDEX idx_trainings_resume_id
    ON trainings(resume_id);

CREATE INDEX idx_certifications_resume_id
    ON certifications(resume_id);

CREATE INDEX idx_awards_resume_id
    ON awards(resume_id);

CREATE INDEX idx_overseas_experiences_resume_id
    ON overseas_experiences(resume_id);

CREATE INDEX idx_language_proficiencies_resume_id
    ON language_proficiencies(resume_id);

-- =============================================
-- 18. updated_at 자동 갱신 함수 및 트리거
-- DEFAULT만 설정하면 UPDATE 시각은 자동 변경되지 않음
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

CREATE TRIGGER trg_cover_letter_items_updated_at
    BEFORE UPDATE ON cover_letter_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 자기소개서 청크의 임베딩 처리 완료 시각을 기록합니다.
ALTER TABLE cover_letter_chunks
    ADD COLUMN embedding_updated_at TIMESTAMPTZ;

-- 사용자 스킬의 근거가 이력서 청크 또는 자기소개서 청크 중 하나를 가리키도록 확장합니다.
ALTER TABLE user_skill_evidences
    ALTER COLUMN resume_chunk_id DROP NOT NULL,
    ADD COLUMN cover_letter_chunk_id BIGINT,
    ADD CONSTRAINT fk_user_skill_evidence_cover_letter_chunk
        FOREIGN KEY (cover_letter_chunk_id)
        REFERENCES cover_letter_chunks(id)
        ON DELETE CASCADE,
    ADD CONSTRAINT uk_user_skill_evidence_cover_letter_chunk
        UNIQUE (user_skill_id, cover_letter_chunk_id),
    ADD CONSTRAINT ck_user_skill_evidence_single_source
        CHECK (
            (resume_chunk_id IS NOT NULL AND cover_letter_chunk_id IS NULL)
            OR
            (resume_chunk_id IS NULL AND cover_letter_chunk_id IS NOT NULL)
        );

CREATE INDEX idx_user_skill_evidences_cover_letter_chunk_id
    ON user_skill_evidences(cover_letter_chunk_id);

-- 특정 채용공고를 기준으로 생성한 자기소개서 전체 피드백입니다.
CREATE TABLE cover_letter_feedbacks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cover_letter_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    overall_score NUMERIC(5, 2),
    summary TEXT,
    ai_model VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letter_feedback_cover_letter
        FOREIGN KEY (cover_letter_id)
        REFERENCES cover_letters(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cover_letter_feedback_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_cover_letter_feedback_overall_score
        CHECK (
            overall_score IS NULL
            OR overall_score BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_cover_letter_feedbacks_cover_letter_id
    ON cover_letter_feedbacks(cover_letter_id);

CREATE INDEX idx_cover_letter_feedbacks_job_posting_id
    ON cover_letter_feedbacks(job_posting_id);

CREATE INDEX idx_cover_letter_feedbacks_created_at
    ON cover_letter_feedbacks(created_at);

-- 전체 피드백에 포함되는 자기소개서 문항별 상세 피드백입니다.
CREATE TABLE cover_letter_item_feedbacks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    cover_letter_item_id BIGINT NOT NULL,
    score NUMERIC(5, 2),
    strengths TEXT,
    improvements TEXT,
    suggested_answer TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letter_item_feedback_feedback
        FOREIGN KEY (feedback_id)
        REFERENCES cover_letter_feedbacks(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cover_letter_item_feedback_item
        FOREIGN KEY (cover_letter_item_id)
        REFERENCES cover_letter_items(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_cover_letter_item_feedback
        UNIQUE (feedback_id, cover_letter_item_id),

    CONSTRAINT ck_cover_letter_item_feedback_score
        CHECK (
            score IS NULL
            OR score BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_cover_letter_item_feedbacks_feedback_id
    ON cover_letter_item_feedbacks(feedback_id);

CREATE INDEX idx_cover_letter_item_feedbacks_item_id
    ON cover_letter_item_feedbacks(cover_letter_item_id);

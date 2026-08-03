-- 계획서 10절 권장: LLM 추출 입력 해시로 중복 추출 호출을 생략하기 위한 메타데이터.
CREATE TABLE IF NOT EXISTS job_posting_extraction_meta (
    job_posting_id   bigint       NOT NULL,
    input_hash       varchar(64)  NOT NULL,
    prompt_version  varchar(32)  NOT NULL,
    extraction_model varchar(64)  NOT NULL,
    tech_stacks_json jsonb        NOT NULL DEFAULT '[]'::jsonb,
    benefits_json    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),

    PRIMARY KEY (job_posting_id),
    FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_extraction_meta_input_hash
    ON job_posting_extraction_meta (input_hash);

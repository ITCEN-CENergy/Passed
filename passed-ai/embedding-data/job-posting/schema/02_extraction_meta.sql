-- 동일한 공고 원문에 대한 반복 LLM 호출을 피하기 위한 구조화 추출 캐시.
CREATE TABLE IF NOT EXISTS job_posting_extraction_meta (
    job_posting_id    bigint       NOT NULL,
    input_hash        varchar(64)  NOT NULL,
    prompt_version    varchar(32)  NOT NULL,
    extraction_model  varchar(64)  NOT NULL,
    tech_stacks_json  jsonb        NOT NULL DEFAULT '[]'::jsonb,
    benefits_json     jsonb        NOT NULL DEFAULT '[]'::jsonb,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    PRIMARY KEY (job_posting_id),
    FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE
);

-- 입력 해시가 같은 추출 결과를 빠르게 확인하기 위한 인덱스.
CREATE INDEX IF NOT EXISTS ix_extraction_meta_input_hash
    ON job_posting_extraction_meta (input_hash);

-- Q. 별칭을 skills.aliases JSONB로 넣지 않는 이유는 무엇인가요?
-- A. 별칭마다 임베딩과 활성 상태를 관리하고, 실제로 어떤 별칭이 매핑됐는지
--    추적하기 위해 한 별칭을 한 행으로 저장합니다.
CREATE TABLE skill_aliases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    normalized_alias VARCHAR(100) NOT NULL,
    embedding VECTOR(1536),
    embedding_model VARCHAR(100),
    source VARCHAR(30) NOT NULL DEFAULT 'CURATED',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_skill_alias_skill
        FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE,
    CONSTRAINT uk_skill_alias
        UNIQUE (skill_id, normalized_alias),
    CONSTRAINT ck_skill_alias_text
        CHECK (BTRIM(alias) <> '' AND BTRIM(normalized_alias) <> ''),
    CONSTRAINT ck_skill_alias_source
        CHECK (source IN ('CURATED', 'EXTRACTED', 'REVIEWED'))
);

CREATE INDEX idx_skill_aliases_normalized
    ON skill_aliases(normalized_alias)
    WHERE is_active = TRUE;

CREATE INDEX idx_skill_aliases_embedding_hnsw
    ON skill_aliases USING hnsw (embedding vector_cosine_ops);

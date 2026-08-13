CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE skills
    ADD COLUMN IF NOT EXISTS embedding VECTOR(1536);

DO $$
BEGIN
    IF (
        SELECT format_type(a.atttypid, a.atttypmod)
        FROM pg_attribute a
        WHERE a.attrelid = 'skills'::regclass
          AND a.attname = 'embedding'
          AND NOT a.attisdropped
    ) <> 'vector(1536)' THEN
        RAISE EXCEPTION 'skills.embedding must be vector(1536)';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_skills_embedding_hnsw
    ON skills USING hnsw (embedding vector_cosine_ops);

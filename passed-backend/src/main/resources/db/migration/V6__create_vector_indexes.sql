CREATE INDEX idx_cover_letter_items_embedding_hnsw
    ON cover_letter_items
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_resume_chunks_embedding_hnsw
    ON resume_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_skills_embedding_hnsw
    ON skills
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_job_posting_chunks_embedding_hnsw
    ON job_posting_chunks
    USING hnsw (embedding vector_cosine_ops);

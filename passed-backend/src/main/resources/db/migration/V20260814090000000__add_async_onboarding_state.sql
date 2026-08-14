ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE resumes
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE resumes
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE TABLE user_skill_extraction_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(40) NOT NULL,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_user_skill_extraction_run_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_skill_extraction_run_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_user_skill_extraction_run_stage
        CHECK (stage IN (
            'DOCUMENT_ANALYSIS', 'SKILL_EXTRACTION',
            'COMPETENCY_ORGANIZATION', 'COMPLETED', 'FAILED'
        ))
);

CREATE INDEX idx_user_skill_extraction_runs_user_created
    ON user_skill_extraction_runs(user_id, created_at DESC);

CREATE UNIQUE INDEX uk_user_skill_extraction_runs_processing_user
    ON user_skill_extraction_runs(user_id)
    WHERE status = 'PROCESSING';

CREATE TRIGGER trg_user_skill_extraction_runs_updated_at
    BEFORE UPDATE ON user_skill_extraction_runs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

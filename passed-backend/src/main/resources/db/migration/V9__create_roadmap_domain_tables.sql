-- Bring the legacy V5 roadmap schema in line with the roadmap domain model.

DROP INDEX IF EXISTS idx_roadmaps_job_posting_id;
DROP INDEX IF EXISTS idx_roadmaps_report_id;
ALTER TABLE roadmaps DROP CONSTRAINT IF EXISTS fk_roadmap_job_posting;
ALTER TABLE roadmaps DROP CONSTRAINT IF EXISTS fk_roadmap_report;
ALTER TABLE roadmaps DROP COLUMN IF EXISTS job_posting_id;
ALTER TABLE roadmaps DROP COLUMN IF EXISTS report_id;
ALTER TABLE roadmaps DROP COLUMN IF EXISTS last_replanned_at;
ALTER TABLE roadmaps ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE roadmaps ALTER COLUMN title DROP NOT NULL;
ALTER TABLE roadmaps ALTER COLUMN status SET DEFAULT 'CREATING';
UPDATE roadmaps SET status = 'CREATING' WHERE status IS NULL;
ALTER TABLE roadmaps ALTER COLUMN status SET NOT NULL;
ALTER TABLE roadmaps ALTER COLUMN total_estimated_minutes SET DEFAULT 0;
UPDATE roadmaps SET total_estimated_minutes = 0 WHERE total_estimated_minutes IS NULL;
ALTER TABLE roadmaps ALTER COLUMN total_estimated_minutes SET NOT NULL;
ALTER TABLE roadmaps ALTER COLUMN progress_rate SET DEFAULT 0;
UPDATE roadmaps SET progress_rate = 0 WHERE progress_rate IS NULL;
ALTER TABLE roadmaps ALTER COLUMN progress_rate SET NOT NULL;
ALTER TABLE roadmaps DROP CONSTRAINT IF EXISTS ck_roadmaps_status;
ALTER TABLE roadmaps ADD CONSTRAINT ck_roadmaps_status
    CHECK (status IN ('CREATING', 'ACTIVE', 'COMPLETED', 'FAILED'));
CREATE INDEX IF NOT EXISTS idx_roadmaps_user_created_id
    ON roadmaps(user_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS roadmap_job_postings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roadmap_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    report_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roadmap_job_postings_roadmap FOREIGN KEY (roadmap_id)
        REFERENCES roadmaps(id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_job_postings_job_posting FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_job_postings_report FOREIGN KEY (report_id)
        REFERENCES analysis_reports(id) ON DELETE SET NULL,
    CONSTRAINT uk_roadmap_job_postings_roadmap_job UNIQUE (roadmap_id, job_posting_id)
);
CREATE INDEX IF NOT EXISTS idx_roadmap_job_postings_job_posting
    ON roadmap_job_postings(job_posting_id);

DROP INDEX IF EXISTS idx_roadmap_skills_skill_id;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS fk_roadmap_skill_skill;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS uk_roadmap_skill;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS ck_roadmap_skill_importance;
ALTER TABLE roadmap_skills DROP COLUMN IF EXISTS skill_id;
ALTER TABLE roadmap_skills DROP COLUMN IF EXISTS importance;
ALTER TABLE roadmap_skills DROP COLUMN IF EXISTS current_level;
ALTER TABLE roadmap_skills DROP COLUMN IF EXISTS target_level;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS standard_competency_id BIGINT;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS standard_competency_name VARCHAR(200);
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS current_level INT;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS target_level INT;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS requirement_type VARCHAR(50);
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS gap_level INT;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS frequency INT;
ALTER TABLE roadmap_skills ADD COLUMN IF NOT EXISTS priority_score NUMERIC(10, 4);
UPDATE roadmap_skills SET standard_competency_id = id,
    standard_competency_name = 'legacy-' || id, category = 'TECHNICAL_SKILL',
    current_level = 0, target_level = 1, requirement_type = 'RELATED', gap_level = 1,
    frequency = 1, priority_score = 0, priority = COALESCE(priority, 1),
    estimated_minutes = COALESCE(estimated_minutes, 0), progress_rate = COALESCE(progress_rate, 0);
ALTER TABLE roadmap_skills ALTER COLUMN standard_competency_id SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN standard_competency_name SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN category SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN current_level SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN target_level SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN requirement_type SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN gap_level SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN frequency SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN priority_score SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN priority SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN estimated_minutes SET DEFAULT 0;
ALTER TABLE roadmap_skills ALTER COLUMN estimated_minutes SET NOT NULL;
ALTER TABLE roadmap_skills ALTER COLUMN progress_rate SET DEFAULT 0;
ALTER TABLE roadmap_skills ALTER COLUMN progress_rate SET NOT NULL;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS uk_roadmap_skills_roadmap_competency;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS ck_roadmap_skills_levels;
ALTER TABLE roadmap_skills DROP CONSTRAINT IF EXISTS ck_roadmap_skills_frequency;
ALTER TABLE roadmap_skills ADD CONSTRAINT uk_roadmap_skills_roadmap_competency UNIQUE (roadmap_id, standard_competency_id);
ALTER TABLE roadmap_skills ADD CONSTRAINT ck_roadmap_skills_levels CHECK (current_level >= 0 AND target_level >= 0 AND gap_level > 0);
ALTER TABLE roadmap_skills ADD CONSTRAINT ck_roadmap_skills_frequency CHECK (frequency >= 1);
CREATE INDEX IF NOT EXISTS idx_roadmap_skills_competency ON roadmap_skills(standard_competency_id);

CREATE TABLE IF NOT EXISTS roadmap_skill_sources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roadmap_skill_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    report_id BIGINT,
    standard_competency_id BIGINT NOT NULL,
    standard_competency_name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    current_level INT NOT NULL,
    current_evidence TEXT,
    requirement_type VARCHAR(50) NOT NULL,
    target_level INT NOT NULL,
    gap_level INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roadmap_skill_sources_skill FOREIGN KEY (roadmap_skill_id)
        REFERENCES roadmap_skills(id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_skill_sources_posting FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_skill_sources_report FOREIGN KEY (report_id)
        REFERENCES analysis_reports(id) ON DELETE SET NULL,
    CONSTRAINT uk_roadmap_skill_sources_skill_job_competency
        UNIQUE (roadmap_skill_id, job_posting_id, standard_competency_id),
    CONSTRAINT ck_roadmap_skill_sources_levels CHECK (current_level >= 0 AND target_level >= 0 AND gap_level > 0)
);
CREATE INDEX IF NOT EXISTS idx_roadmap_skill_sources_job_posting ON roadmap_skill_sources(job_posting_id);

DROP INDEX IF EXISTS idx_milestones_skill_id;
ALTER TABLE milestones DROP CONSTRAINT IF EXISTS fk_milestone_skill;
ALTER TABLE milestones DROP CONSTRAINT IF EXISTS ck_milestone_estimated_minutes;
ALTER TABLE milestones DROP COLUMN IF EXISTS skill_id;
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS standard_competency_id BIGINT;
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS learning_objective TEXT;
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS start_level INT;
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS target_level INT;
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS milestone_type VARCHAR(50);
ALTER TABLE milestones ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
UPDATE milestones SET standard_competency_id = id, learning_objective = COALESCE(description, ''),
    completion_criteria = COALESCE(completion_criteria, ''), start_level = 0, target_level = 1,
    milestone_type = 'CONCEPT', difficulty = COALESCE(difficulty, 'BEGINNER'),
    estimated_minutes = GREATEST(COALESCE(estimated_minutes, 1), 1),
    status = COALESCE(status, 'NOT_STARTED'), progress_rate = COALESCE(progress_rate, 0);
ALTER TABLE milestones ALTER COLUMN standard_competency_id SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN learning_objective SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN completion_criteria SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN start_level SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN target_level SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN milestone_type SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN difficulty SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN estimated_minutes SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN status SET DEFAULT 'NOT_STARTED';
ALTER TABLE milestones ALTER COLUMN status SET NOT NULL;
ALTER TABLE milestones ALTER COLUMN progress_rate SET DEFAULT 0;
ALTER TABLE milestones ALTER COLUMN progress_rate SET NOT NULL;
ALTER TABLE milestones DROP CONSTRAINT IF EXISTS ck_milestones_levels;
ALTER TABLE milestones DROP CONSTRAINT IF EXISTS ck_milestones_estimated_minutes;
ALTER TABLE milestones ADD CONSTRAINT ck_milestones_levels CHECK (start_level >= 0 AND target_level > start_level);
ALTER TABLE milestones ADD CONSTRAINT ck_milestones_estimated_minutes CHECK (estimated_minutes >= 1);
CREATE INDEX IF NOT EXISTS idx_milestones_user_competency ON milestones(user_id, standard_competency_id);

UPDATE roadmap_milestones SET learning_order = id WHERE learning_order IS NULL;
ALTER TABLE roadmap_milestones ALTER COLUMN learning_order SET NOT NULL;
ALTER TABLE roadmap_milestones ADD COLUMN IF NOT EXISTS reuse_type VARCHAR(50) NOT NULL DEFAULT 'NEW';
ALTER TABLE roadmap_milestones ADD COLUMN IF NOT EXISTS reuse_reason TEXT;
ALTER TABLE roadmap_milestones ADD COLUMN IF NOT EXISTS is_required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE roadmap_milestones DROP CONSTRAINT IF EXISTS uk_roadmap_milestones_skill_order;
ALTER TABLE roadmap_milestones DROP CONSTRAINT IF EXISTS ck_roadmap_milestones_reuse_type;
ALTER TABLE roadmap_milestones ADD CONSTRAINT uk_roadmap_milestones_skill_order UNIQUE (roadmap_skill_id, learning_order);
ALTER TABLE roadmap_milestones ADD CONSTRAINT ck_roadmap_milestones_reuse_type CHECK (reuse_type IN ('NEW', 'REUSED', 'EXTENDED'));

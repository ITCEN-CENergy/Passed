ALTER TABLE roadmaps
    ADD COLUMN daily_study_minutes INTEGER NOT NULL DEFAULT 60;

ALTER TABLE roadmaps
    ADD CONSTRAINT ck_roadmaps_daily_study_minutes
        CHECK (daily_study_minutes BETWEEN 30 AND 480);

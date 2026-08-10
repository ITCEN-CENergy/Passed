-- Preserve the initially calculated roadmap ETA as the schedule baseline.
-- CREATING and FAILED legacy roadmaps may not have an ETA, so the column remains nullable.
ALTER TABLE roadmaps
    ADD COLUMN baseline_end_date DATE;

UPDATE roadmaps
SET baseline_end_date = estimated_end_date
WHERE estimated_end_date IS NOT NULL;

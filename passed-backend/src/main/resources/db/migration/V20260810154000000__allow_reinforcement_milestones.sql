ALTER TABLE milestones
    DROP CONSTRAINT IF EXISTS ck_milestones_levels;

ALTER TABLE milestones
    ADD CONSTRAINT ck_milestones_levels CHECK (
        start_level BETWEEN 0 AND 3
        AND target_level BETWEEN 1 AND 3
        AND target_level >= start_level
    );

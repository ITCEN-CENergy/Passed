ALTER TABLE roadmap_skills
    DROP CONSTRAINT IF EXISTS ck_roadmap_skills_levels;

ALTER TABLE roadmap_skills
    ADD CONSTRAINT ck_roadmap_skills_levels CHECK (
        current_level BETWEEN 0 AND 3
        AND target_level BETWEEN 1 AND 3
        AND gap_level = GREATEST(target_level - current_level, 0)
    );

ALTER TABLE roadmap_skill_sources
    DROP CONSTRAINT IF EXISTS ck_roadmap_skill_sources_levels;

ALTER TABLE roadmap_skill_sources
    ADD CONSTRAINT ck_roadmap_skill_sources_levels CHECK (
        current_level BETWEEN 0 AND 3
        AND target_level BETWEEN 1 AND 3
        AND gap_level = GREATEST(target_level - current_level, 0)
    );

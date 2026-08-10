ALTER TABLE roadmaps ADD COLUMN IF NOT EXISTS generation_key TEXT;

WITH keys AS (
    SELECT r.id,
           STRING_AGG(rjp.job_posting_id::TEXT, ',' ORDER BY rjp.job_posting_id) AS generation_key
    FROM roadmaps r
    JOIN roadmap_job_postings rjp ON rjp.roadmap_id = r.id
    GROUP BY r.id
), ranked AS (
    SELECT r.id,
           keys.generation_key,
           ROW_NUMBER() OVER (
               PARTITION BY r.user_id, keys.generation_key
               ORDER BY CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END, r.id
           ) AS active_rank
    FROM roadmaps r
    JOIN keys ON keys.id = r.id
    WHERE r.status IN ('CREATING', 'ACTIVE')
)
UPDATE roadmaps r
SET generation_key = keys.generation_key
FROM keys
WHERE r.id = keys.id
  AND (
      r.status NOT IN ('CREATING', 'ACTIVE')
      OR EXISTS (
          SELECT 1
          FROM ranked
          WHERE ranked.id = r.id
            AND ranked.active_rank = 1
      )
  );

CREATE UNIQUE INDEX IF NOT EXISTS uk_roadmaps_active_generation
    ON roadmaps(user_id, generation_key)
    WHERE generation_key IS NOT NULL
      AND status IN ('CREATING', 'ACTIVE');

CREATE INDEX IF NOT EXISTS idx_roadmaps_user_generation_key
    ON roadmaps(user_id, generation_key)
    WHERE generation_key IS NOT NULL;

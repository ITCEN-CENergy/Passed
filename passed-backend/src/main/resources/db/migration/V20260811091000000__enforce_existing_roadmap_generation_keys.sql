WITH keys AS (
    SELECT r.id,
           STRING_AGG(rjp.job_posting_id::TEXT, ',' ORDER BY rjp.job_posting_id) AS generation_key
    FROM roadmaps r
    JOIN roadmap_job_postings rjp ON rjp.roadmap_id = r.id
    WHERE r.generation_key IS NULL
    GROUP BY r.id
)
UPDATE roadmaps r
SET generation_key = keys.generation_key
FROM keys
WHERE r.id = keys.id;

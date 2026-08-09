-- The production job-posting skill seed is keyed by job_posting IDs imported
-- outside Flyway. A clean CI database does not contain that external import,
-- so provide only the referenced rows needed to validate all migrations.

INSERT INTO industries (id, industry_name)
OVERRIDING SYSTEM VALUE
VALUES (1, 'CI migration fixture')
ON CONFLICT (id) DO NOTHING;

INSERT INTO job_roles (id, industry_id, job_role_name)
OVERRIDING SYSTEM VALUE
VALUES (1, 1, 'CI migration fixture')
ON CONFLICT (id) DO NOTHING;

INSERT INTO job_postings (id, title, company_id, job_role_id)
OVERRIDING SYSTEM VALUE
SELECT
    generated_id,
    'CI migration fixture ' || generated_id,
    (SELECT MIN(id) FROM companies),
    1
FROM generate_series(1, 4730) AS generated_id
ON CONFLICT (id) DO NOTHING;

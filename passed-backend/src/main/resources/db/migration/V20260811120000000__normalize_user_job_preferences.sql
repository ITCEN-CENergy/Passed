ALTER TABLE users
    ADD COLUMN desired_industry_id BIGINT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT fk_users_desired_industry
        FOREIGN KEY (desired_industry_id)
        REFERENCES industries(id)
        ON DELETE SET NULL;

CREATE INDEX idx_users_desired_industry_id
    ON users(desired_industry_id);

CREATE TABLE user_desired_job_roles (
    user_id BIGINT NOT NULL,
    job_role_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_desired_job_roles
        PRIMARY KEY (user_id, job_role_id),
    CONSTRAINT fk_user_desired_job_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_desired_job_roles_job_role
        FOREIGN KEY (job_role_id)
        REFERENCES job_roles(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_user_desired_job_roles_job_role_id
    ON user_desired_job_roles(job_role_id);

UPDATE users AS user_account
SET desired_industry_id = industry.id
FROM industries AS industry
WHERE user_account.field = industry.industry_name;

INSERT INTO user_desired_job_roles (user_id, job_role_id)
SELECT DISTINCT user_account.id, job_role.id
FROM users AS user_account
JOIN industries AS industry
    ON industry.id = user_account.desired_industry_id
CROSS JOIN LATERAL jsonb_array_elements_text(
    COALESCE(user_account.desired_jobs, '[]'::jsonb)
) AS desired_job(name)
JOIN job_roles AS job_role
    ON job_role.industry_id = industry.id
    AND job_role.job_role_name = desired_job.name
ON CONFLICT DO NOTHING;

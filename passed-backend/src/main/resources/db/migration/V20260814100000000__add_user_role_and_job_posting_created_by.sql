ALTER TABLE users ADD COLUMN role VARCHAR(20);
UPDATE users SET role = 'GENERAL_USER' WHERE role IS NULL;
ALTER TABLE users ALTER COLUMN role SET NOT NULL;

ALTER TABLE job_postings ADD COLUMN created_by_user_id BIGINT;
ALTER TABLE job_postings ADD CONSTRAINT fk_job_postings_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id);

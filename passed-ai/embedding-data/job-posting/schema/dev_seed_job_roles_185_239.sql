-- 개발/로컬 테스트용 임시 산업·직무 데이터
--
-- 목적:
--   job_postings_185_239 CSV가 참조하는 job_roles.id 185~239를 채운다.
--
-- 주의:
--   운영 DB에서 실행하지 않는다.
--   실제 산업/직무 기준정보를 확보하면 이 임시 데이터를 교체해야 한다.
--   이 파일은 job_posting_pipeline.db.init_schema()에서 자동 실행되지 않는다.

BEGIN;

-- job_roles.industry_id는 필수 외래키이므로 개발용 산업 한 행을 먼저 준비한다.
INSERT INTO industries (
    id,
    industry_name
)
VALUES (
    0,
    '임시 산업'
)
ON CONFLICT DO NOTHING;

-- industry id=0이 다른 고유키 충돌로 생성되지 않은 경우 잘못된 seed를 막는다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM industries WHERE id = 0) THEN
        RAISE EXCEPTION '개발용 industries.id=0을 준비할 수 없습니다.';
    END IF;
END;
$$;

INSERT INTO job_roles (
    id,
    industry_id,
    job_role_name
)
SELECT
    role_id,
    0,
    format('임시 직무 %s', role_id)
FROM generate_series(185, 239) AS series(role_id)
ON CONFLICT DO NOTHING;

-- 명시적 id 삽입 뒤 자동 생성 시퀀스를 현재 MAX(id) 다음으로 맞춘다.
SELECT setval(
    pg_get_serial_sequence('industries', 'id'),
    COALESCE((SELECT MAX(id) FROM industries), 0) + 1,
    false
);

SELECT setval(
    pg_get_serial_sequence('job_roles', 'id'),
    COALESCE((SELECT MAX(id) FROM job_roles), 0) + 1,
    false
);

COMMIT;

-- 실행 결과 확인
SELECT
    COUNT(*) FILTER (WHERE id BETWEEN 185 AND 239) AS job_role_count_185_239,
    MIN(id) FILTER (WHERE id BETWEEN 185 AND 239) AS min_job_role_id,
    MAX(id) FILTER (WHERE id BETWEEN 185 AND 239) AS max_job_role_id
FROM job_roles;

-- 정상 결과:
-- job_role_count_185_239 = 55
-- min_job_role_id        = 185
-- max_job_role_id        = 239

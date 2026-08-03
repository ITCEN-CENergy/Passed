-- 개발/로컬 테스트용 임시 회사 데이터
--
-- 목적:
--   채용공고 CSV 적재 전 검사에서 요구하는 companies.id 0~159를 채운다.
--
-- 주의:
--   운영 DB에서 실행하지 않는다.
--   기존 id 또는 company_name과 충돌하는 행은 변경하지 않고 건너뛴다.
--   이 파일은 job_posting_pipeline.db.init_schema()에서 자동 실행되지 않는다.

BEGIN;

INSERT INTO companies (
    id,
    company_name,
    company_size,
    talent_profile,
    benefits
)
SELECT
    company_id,
    format('임시 회사 %s', lpad(company_id::text, 3, '0')),
    NULL,
    NULL,
    NULL
FROM generate_series(0, 159) AS series(company_id)
ON CONFLICT DO NOTHING;

-- 명시적 id 삽입 뒤 자동 생성 시퀀스가 기존 MAX(id) 다음 값을 사용하도록 맞춘다.
SELECT setval(
    pg_get_serial_sequence('companies', 'id'),
    COALESCE((SELECT MAX(id) FROM companies), 0) + 1,
    false
);

COMMIT;

-- 실행 결과 확인
SELECT
    COUNT(*) FILTER (WHERE id BETWEEN 0 AND 159) AS company_count_0_159,
    MIN(id) FILTER (WHERE id BETWEEN 0 AND 159) AS min_company_id,
    MAX(id) FILTER (WHERE id BETWEEN 0 AND 159) AS max_company_id
FROM companies;

-- 정상 결과:
-- company_count_0_159 = 160
-- min_company_id      = 0
-- max_company_id      = 159

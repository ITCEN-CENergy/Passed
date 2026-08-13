-- V20260812070039274의 개발용 사용자 스킬이 참조하는 사용자를 먼저 보장합니다.
-- 이미 id 178 사용자가 있는 환경에서는 기존 정보를 변경하지 않습니다.
INSERT INTO users (id, name, email, password, field, desired_jobs)
    OVERRIDING SYSTEM VALUE
VALUES (
    178,
    '로드맵테스트사용자',
    'roadmap-test-178@passed.dev',
    'dev-only-not-hashed',
    'AI·개발·데이터',
    '["AI서비스개발자"]'::jsonb
)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('users', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM users), 1),
    TRUE
);

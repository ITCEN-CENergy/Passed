-- 추천 API 개발 검증용 사용자입니다.
INSERT INTO users (id, name, email, password, field, desired_jobs)
OVERRIDING SYSTEM VALUE
VALUES (
    2,
    '추천테스트사용자',
    'recommendation-test@passed.dev',
    'dev-only-not-hashed',
    'AI·개발·데이터',
    '["AI서비스개발자", "AI보안전문가", "AI/ML엔지니어"]'::jsonb
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    password = EXCLUDED.password,
    field = EXCLUDED.field,
    desired_jobs = EXCLUDED.desired_jobs;

SELECT setval(
    pg_get_serial_sequence('users', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM users), 1),
    TRUE
);

INSERT INTO user_skills (user_id, skill_id, skill_level, is_important)
VALUES
    (2, 1498, 1, FALSE),
    (2, 1409, 1, TRUE),
    (2, 1589, 1, FALSE),
    (2, 1355, 1, FALSE),
    (2, 1344, 3, TRUE),
    (2, 1339, 2, FALSE),
    (2, 1328, 3, FALSE),
    (2, 548, 3, TRUE),
    (2, 1225, 2, FALSE),
    (2, 1146, 3, FALSE),
    (2, 843, 3, FALSE),
    (2, 16, 1, FALSE),
    (2, 13, 2, FALSE),
    (2, 107, 3, TRUE),
    (2, 12, 3, TRUE)
ON CONFLICT (user_id, skill_id) DO UPDATE SET
    skill_level = EXCLUDED.skill_level,
    is_important = EXCLUDED.is_important,
    updated_at = CURRENT_TIMESTAMP;

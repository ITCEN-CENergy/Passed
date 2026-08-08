-- 추천 API 개발 검증용 사용자입니다.
INSERT INTO users (id, name, email, password, field, desired_jobs)
    OVERRIDING SYSTEM VALUE
VALUES (
           3,
           '추천테스트사용자2',
           'recommendation-test2@passed.dev',
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
    (3, 13, 3, TRUE),
    (3, 16, 3, TRUE),
    (3, 27, 3, TRUE),
    (3, 96, 3, TRUE),
    (3, 107, 3, TRUE),
    (3, 108, 3, TRUE),
    (3, 158, 3, TRUE),
    (3, 191, 3, TRUE),
    (3, 282, 3, TRUE),
    (3, 336, 3, TRUE),
    (3, 339, 3, TRUE),
    (3, 490, 3, TRUE),
    (3, 498, 3, TRUE),
    (3, 548, 3, TRUE),
    (3, 554, 3, TRUE),
    (3, 575, 3, TRUE),
    (3, 587, 3, TRUE),
    (3, 695, 3, TRUE),
    (3, 700, 3, TRUE),
    (3, 843, 3, TRUE),
    (3, 858, 3, TRUE),
    (3, 873, 3, TRUE),
    (3, 883, 3, TRUE),
    (3, 916, 3, TRUE),
    (3, 1146, 3, TRUE),
    (3, 1147, 3, TRUE),
    (3, 1225, 3, TRUE),
    (3, 1262, 3, TRUE),
    (3, 1282, 3, TRUE),
    (3, 1335, 3, TRUE),
    (3, 1339, 3, TRUE),
    (3, 1343, 3, TRUE),
    (3, 1344, 3, TRUE),
    (3, 1355, 3, TRUE),
    (3, 1409, 3, TRUE),
    (3, 1498, 3, TRUE)

    ON CONFLICT (user_id, skill_id) DO UPDATE SET
    skill_level = EXCLUDED.skill_level,
    is_important = EXCLUDED.is_important,
    updated_at = CURRENT_TIMESTAMP;
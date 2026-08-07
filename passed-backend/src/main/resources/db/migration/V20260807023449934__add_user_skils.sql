INSERT INTO user_skills (user_id, skill_id, skill_level, is_important)
VALUES
    (2, 339, 3, TRUE),
    (2, 700, 2, FALSE),
    (2, 933, 1, FALSE),
    (2, 156, 2, TRUE),
    (2, 694, 3, FALSE),
    (2, 220, 1, FALSE),
    (2, 1241, 1, FALSE),
    (2, 1273, 2, FALSE),
    (2, 892, 3, TRUE),
    (2, 893, 1, FALSE)

    ON CONFLICT (user_id, skill_id) DO UPDATE SET
    skill_level = EXCLUDED.skill_level,
    is_important = EXCLUDED.is_important,
    updated_at = CURRENT_TIMESTAMP;

UPDATE recommendation_runs
SET
    status = 'FAILED',
    completed_at = CURRENT_TIMESTAMP,
    failure_message = 'Reset for development test'
WHERE user_id = 2
  AND status = 'PROCESSING';
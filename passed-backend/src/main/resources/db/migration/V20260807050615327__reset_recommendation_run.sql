UPDATE recommendation_runs
SET
    status = 'FAILED',
    completed_at = CURRENT_TIMESTAMP,
    failure_message = 'Reset for development test'
WHERE user_id = 2
  AND status = 'PROCESSING';
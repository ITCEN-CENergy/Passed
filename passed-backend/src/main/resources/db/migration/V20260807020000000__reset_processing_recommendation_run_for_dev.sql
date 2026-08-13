-- 추천 API 재호출 검증을 위한 개발용 일회성 상태 초기화입니다.
UPDATE recommendation_runs
SET
    status = 'FAILED',
    completed_at = CURRENT_TIMESTAMP,
    failure_message = 'Reset for development test'
WHERE user_id = 2
  AND status = 'PROCESSING';

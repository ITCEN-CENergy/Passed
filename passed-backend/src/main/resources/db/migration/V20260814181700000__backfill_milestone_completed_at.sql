UPDATE milestones
SET completed_at = updated_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

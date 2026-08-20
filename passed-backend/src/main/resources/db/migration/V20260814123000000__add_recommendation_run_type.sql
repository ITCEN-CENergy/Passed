ALTER TABLE recommendation_runs
    ADD COLUMN recommendation_type VARCHAR(32);

-- 기존 생성 규칙상 여러 공고 추천만 preference_snapshot에 industryId를 저장합니다.
-- 실행 ID나 사용자 ID를 사용하지 않으므로 팀원마다 기존 데이터가 달라도 동일하게 적용됩니다.
UPDATE recommendation_runs
SET recommendation_type = CASE
    WHEN preference_snapshot ? 'industryId' THEN 'MULTIPLE_POSTINGS'
    ELSE 'SINGLE_POSTING'
END
WHERE recommendation_type IS NULL;

ALTER TABLE recommendation_runs
    ALTER COLUMN recommendation_type SET NOT NULL,
    ADD CONSTRAINT ck_rec_run_recommendation_type
        CHECK (recommendation_type IN ('MULTIPLE_POSTINGS', 'SINGLE_POSTING'));

CREATE INDEX idx_rec_runs_user_type_completed_at
    ON recommendation_runs(user_id, recommendation_type, completed_at DESC);

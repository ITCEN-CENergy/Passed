ALTER TABLE recommendation_runs
    ADD COLUMN candidate_posting_count INTEGER,
    ADD COLUMN required_qualified_posting_count INTEGER;

ALTER TABLE recommendation_runs
    ADD CONSTRAINT ck_rec_runs_candidate_count_nonnegative
        CHECK (candidate_posting_count IS NULL OR candidate_posting_count >= 0),
    ADD CONSTRAINT ck_rec_runs_qualified_count_nonnegative
        CHECK (required_qualified_posting_count IS NULL OR required_qualified_posting_count >= 0),
    ADD CONSTRAINT ck_rec_runs_qualified_not_over_candidate
        CHECK (
            candidate_posting_count IS NULL
            OR required_qualified_posting_count IS NULL
            OR required_qualified_posting_count <= candidate_posting_count
        );

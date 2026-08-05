-- =============================================
-- 1. 추천 점수 정책
-- =============================================

CREATE TABLE recommendation_scoring_policies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_code VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    policy_name VARCHAR(100) NOT NULL,
    description TEXT,
    required_max_score NUMERIC(6, 2) NOT NULL,
    preferred_max_score NUMERIC(6, 2) NOT NULL,
    related_max_score NUMERIC(6, 2) NOT NULL,
    important_bonus_max_score NUMERIC(6, 2) NOT NULL,
    required_coverage_threshold NUMERIC(5, 4) NOT NULL,
    primary_important_match_count INT NOT NULL DEFAULT 1,
    important_required_weight NUMERIC(5, 4) NOT NULL,
    important_preferred_weight NUMERIC(5, 4) NOT NULL,
    important_related_weight NUMERIC(5, 4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,

    CONSTRAINT uk_rec_scoring_policy_code_version
        UNIQUE (policy_code, version),

    CONSTRAINT ck_rec_scoring_policy_code
        CHECK (BTRIM(policy_code) <> ''),

    CONSTRAINT ck_rec_scoring_policy_version
        CHECK (BTRIM(version) <> ''),

    CONSTRAINT ck_rec_scoring_policy_name
        CHECK (BTRIM(policy_name) <> ''),

    CONSTRAINT ck_rec_scoring_policy_score_ranges
        CHECK (
            required_max_score BETWEEN 0 AND 100
            AND preferred_max_score BETWEEN 0 AND 100
            AND related_max_score BETWEEN 0 AND 100
            AND important_bonus_max_score BETWEEN 0 AND 100
            AND required_max_score
                + preferred_max_score
                + related_max_score
                + important_bonus_max_score <= 100
        ),

    CONSTRAINT ck_rec_scoring_policy_coverage_threshold
        CHECK (required_coverage_threshold BETWEEN 0 AND 1),

    CONSTRAINT ck_rec_scoring_policy_important_match_count
        CHECK (primary_important_match_count >= 0),

    CONSTRAINT ck_rec_scoring_policy_weight_ranges
        CHECK (
            important_required_weight BETWEEN 0 AND 1
            AND important_preferred_weight BETWEEN 0 AND 1
            AND important_related_weight BETWEEN 0 AND 1
        ),

    CONSTRAINT ck_rec_scoring_policy_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);

CREATE INDEX idx_rec_scoring_policies_status
    ON recommendation_scoring_policies(status);

CREATE TRIGGER trg_rec_scoring_policies_updated_at
    BEFORE UPDATE ON recommendation_scoring_policies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- =============================================
-- 2. 기본 추천 점수 정책 v1
-- =============================================

INSERT INTO recommendation_scoring_policies (
    policy_code,
    version,
    policy_name,
    required_max_score,
    preferred_max_score,
    related_max_score,
    important_bonus_max_score,
    required_coverage_threshold,
    primary_important_match_count,
    important_required_weight,
    important_preferred_weight,
    important_related_weight,
    status
) VALUES (
    'SKILL_MATCH',
    'v1',
    '스킬 매칭 추천 정책 v1',
    60.00,
    20.00,
    10.00,
    10.00,
    0.5000,
    1,
    1.0000,
    0.7000,
    0.4000,
    'ACTIVE'
);


-- =============================================
-- 3. 추천 등급 규칙
-- =============================================

CREATE TABLE recommendation_grade_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scoring_policy_id BIGINT NOT NULL,
    recommendation_grade VARCHAR(30) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    min_total_score NUMERIC(6, 2) NOT NULL,
    min_required_coverage_rate NUMERIC(5, 4) NOT NULL DEFAULT 0,
    min_required_level_match_rate NUMERIC(5, 4) NOT NULL DEFAULT 0,
    min_important_match_count INT NOT NULL DEFAULT 0,
    priority INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rec_grade_rule_scoring_policy
        FOREIGN KEY (scoring_policy_id)
        REFERENCES recommendation_scoring_policies(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_rec_grade_rule_policy_grade
        UNIQUE (scoring_policy_id, recommendation_grade),

    CONSTRAINT uk_rec_grade_rule_policy_priority
        UNIQUE (scoring_policy_id, priority),

    CONSTRAINT ck_rec_grade_rule_grade
        CHECK (
            recommendation_grade IN (
                'HIGHLY_RECOMMENDED',
                'RECOMMENDED',
                'CHALLENGING',
                'LOW_MATCH'
            )
        ),

    CONSTRAINT ck_rec_grade_rule_display_name
        CHECK (BTRIM(display_name) <> ''),

    CONSTRAINT ck_rec_grade_rule_total_score
        CHECK (min_total_score BETWEEN 0 AND 100),

    CONSTRAINT ck_rec_grade_rule_rate_ranges
        CHECK (
            min_required_coverage_rate BETWEEN 0 AND 1
            AND min_required_level_match_rate BETWEEN 0 AND 1
        ),

    CONSTRAINT ck_rec_grade_rule_important_match_count
        CHECK (min_important_match_count >= 0),

    CONSTRAINT ck_rec_grade_rule_priority
        CHECK (priority > 0)
);

CREATE INDEX idx_rec_grade_rules_scoring_policy_id
    ON recommendation_grade_rules(scoring_policy_id);


-- =============================================
-- 4. 기본 추천 등급 규칙 v1
-- =============================================

INSERT INTO recommendation_grade_rules (
    scoring_policy_id,
    recommendation_grade,
    display_name,
    min_total_score,
    min_required_coverage_rate,
    min_required_level_match_rate,
    min_important_match_count,
    priority
)
SELECT
    p.id,
    v.recommendation_grade,
    v.display_name,
    v.min_total_score,
    v.min_required_coverage_rate,
    v.min_required_level_match_rate,
    v.min_important_match_count,
    v.priority
FROM recommendation_scoring_policies p
CROSS JOIN (
    VALUES
        ('HIGHLY_RECOMMENDED', '매우 적합',   85.00, 0.8000, 0.8000, 1, 40),
        ('RECOMMENDED',        '적합',        70.00, 0.7000, 0.0000, 0, 30),
        ('CHALLENGING',        '도전 가능',   50.00, 0.5000, 0.0000, 0, 20),
        ('LOW_MATCH',          '적합도 낮음',  0.00, 0.5000, 0.0000, 0, 10)
) AS v(
    recommendation_grade,
    display_name,
    min_total_score,
    min_required_coverage_rate,
    min_required_level_match_rate,
    min_important_match_count,
    priority
)
WHERE p.policy_code = 'SKILL_MATCH'
  AND p.version = 'v1';


-- =============================================
-- 5. 추천 실행 이력
-- =============================================

CREATE TABLE recommendation_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scoring_policy_id BIGINT NOT NULL,
    user_skill_snapshot_hash CHAR(64) NOT NULL,
    user_skill_snapshot JSONB NOT NULL,
    preference_snapshot JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    failure_message TEXT,

    CONSTRAINT fk_rec_run_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rec_run_scoring_policy
        FOREIGN KEY (scoring_policy_id)
        REFERENCES recommendation_scoring_policies(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_rec_run_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT ck_rec_run_skill_snapshot_hash
        CHECK (user_skill_snapshot_hash ~ '^[0-9a-fA-F]{64}$'),

    CONSTRAINT ck_rec_run_completed_at
        CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_rec_runs_user_id
    ON recommendation_runs(user_id);

CREATE INDEX idx_rec_runs_status
    ON recommendation_runs(status);

CREATE INDEX idx_rec_runs_user_started_at
    ON recommendation_runs(user_id, started_at);

CREATE INDEX idx_rec_runs_user_skill_snapshot_hash
    ON recommendation_runs(user_id, user_skill_snapshot_hash);


-- =============================================
-- 6. 공고별 추천 결과
-- =============================================

CREATE TABLE job_recommendations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recommendation_run_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    total_score NUMERIC(7, 4) NOT NULL,
    required_score NUMERIC(7, 4) NOT NULL,
    preferred_score NUMERIC(7, 4) NOT NULL,
    related_score NUMERIC(7, 4) NOT NULL,
    important_skill_bonus NUMERIC(7, 4) NOT NULL,
    required_skill_count INT NOT NULL,
    required_owned_count INT NOT NULL,
    required_coverage_rate NUMERIC(5, 4) NOT NULL,
    required_level_match_rate NUMERIC(5, 4) NOT NULL,
    important_skill_count INT NOT NULL,
    important_match_count INT NOT NULL,
    candidate_tier VARCHAR(20) NOT NULL,
    recommendation_grade VARCHAR(30) NOT NULL,
    rank_order INT NOT NULL,
    reason TEXT NOT NULL,
    strengths TEXT,
    weaknesses TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_rec_run
        FOREIGN KEY (recommendation_run_id)
        REFERENCES recommendation_runs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_job_rec_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_job_rec_run_job_posting
        UNIQUE (recommendation_run_id, job_posting_id),

    CONSTRAINT uk_job_rec_run_rank_order
        UNIQUE (recommendation_run_id, rank_order),

    CONSTRAINT ck_job_rec_score_ranges
        CHECK (
            total_score BETWEEN 0 AND 100
            AND required_score BETWEEN 0 AND 100
            AND preferred_score BETWEEN 0 AND 100
            AND related_score BETWEEN 0 AND 100
            AND important_skill_bonus BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_job_rec_total_score
        CHECK (
            total_score = required_score
                + preferred_score
                + related_score
                + important_skill_bonus
        ),

    CONSTRAINT ck_job_rec_required_counts
        CHECK (
            required_skill_count >= 0
            AND required_owned_count >= 0
            AND required_owned_count <= required_skill_count
        ),

    CONSTRAINT ck_job_rec_required_rates
        CHECK (
            required_coverage_rate BETWEEN 0 AND 1
            AND required_level_match_rate BETWEEN 0 AND 1
        ),

    CONSTRAINT ck_job_rec_important_counts
        CHECK (
            important_skill_count >= 0
            AND important_match_count >= 0
            AND important_match_count <= important_skill_count
        ),

    CONSTRAINT ck_job_rec_candidate_tier
        CHECK (candidate_tier IN ('PRIMARY', 'FALLBACK')),

    CONSTRAINT ck_job_rec_grade
        CHECK (
            recommendation_grade IN (
                'HIGHLY_RECOMMENDED',
                'RECOMMENDED',
                'CHALLENGING',
                'LOW_MATCH'
            )
        ),

    CONSTRAINT ck_job_rec_rank_order
        CHECK (rank_order > 0),

    CONSTRAINT ck_job_rec_reason
        CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_job_recommendations_run_id
    ON job_recommendations(recommendation_run_id);

CREATE INDEX idx_job_recommendations_job_posting_id
    ON job_recommendations(job_posting_id);

CREATE INDEX idx_job_recommendations_grade
    ON job_recommendations(recommendation_grade);

CREATE INDEX idx_job_recommendations_candidate_tier
    ON job_recommendations(candidate_tier);

CREATE TRIGGER trg_job_recommendations_updated_at
    BEFORE UPDATE ON job_recommendations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- =============================================
-- 7. 공고 추천 스킬별 계산 상세
-- =============================================

CREATE TABLE job_recommendation_skill_details (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_recommendation_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_type VARCHAR(20) NOT NULL,
    required_level SMALLINT NOT NULL,
    user_level SMALLINT,
    evaluation_type VARCHAR(20) NOT NULL,
    is_owned BOOLEAN NOT NULL,
    is_requirement_satisfied BOOLEAN NOT NULL,
    is_user_important BOOLEAN NOT NULL DEFAULT FALSE,
    match_rate NUMERIC(5, 4) NOT NULL,
    base_max_score NUMERIC(7, 4) NOT NULL,
    base_contribution_score NUMERIC(7, 4) NOT NULL,
    important_bonus_contribution_score NUMERIC(7, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_job_rec_skill_detail_job_rec
        FOREIGN KEY (job_recommendation_id)
        REFERENCES job_recommendations(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_job_rec_skill_detail_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_job_rec_skill_detail_rec_skill
        UNIQUE (job_recommendation_id, skill_id),

    CONSTRAINT ck_job_rec_skill_detail_skill_type
        CHECK (skill_type IN ('REQUIRED', 'PREFERRED', 'RELATED')),

    CONSTRAINT ck_job_rec_skill_detail_required_level
        CHECK (required_level BETWEEN 1 AND 3),

    CONSTRAINT ck_job_rec_skill_detail_user_level
        CHECK (user_level IS NULL OR user_level BETWEEN 1 AND 3),

    CONSTRAINT ck_job_rec_skill_detail_evaluation_type
        CHECK (evaluation_type IN ('LEVEL', 'OWNERSHIP')),

    CONSTRAINT ck_job_rec_skill_detail_ownership
        CHECK (
            (is_owned AND user_level IS NOT NULL)
            OR (NOT is_owned AND user_level IS NULL)
        ),

    CONSTRAINT ck_job_rec_skill_detail_requirement_satisfied
        CHECK (NOT is_requirement_satisfied OR is_owned),

    CONSTRAINT ck_job_rec_skill_detail_match_rate
        CHECK (match_rate BETWEEN 0 AND 1),

    CONSTRAINT ck_job_rec_skill_detail_score_ranges
        CHECK (
            base_max_score BETWEEN 0 AND 100
            AND base_contribution_score BETWEEN 0 AND 100
            AND important_bonus_contribution_score BETWEEN 0 AND 100
            AND base_contribution_score <= base_max_score
        )
);

CREATE INDEX idx_job_rec_skill_details_job_rec_id
    ON job_recommendation_skill_details(job_recommendation_id);

CREATE INDEX idx_job_rec_skill_details_skill_id
    ON job_recommendation_skill_details(skill_id);

CREATE INDEX idx_job_rec_skill_details_rec_skill_type
    ON job_recommendation_skill_details(job_recommendation_id, skill_type);

CREATE INDEX idx_job_rec_skill_details_rec_important
    ON job_recommendation_skill_details(job_recommendation_id, is_user_important);


-- =============================================
-- 8. 기존 분석 리포트 제거
-- analysis_reports를 참조하던 로드맵 컬럼은 유지하고
-- 삭제를 막는 외래키 제약만 제거한다.
-- =============================================

ALTER TABLE roadmap_job_postings
    DROP CONSTRAINT IF EXISTS fk_roadmap_job_postings_report;

ALTER TABLE roadmap_skill_sources
    DROP CONSTRAINT IF EXISTS fk_roadmap_skill_sources_report;

DROP TABLE analysis_reports;

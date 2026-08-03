-- =============================================
-- 1. 분석 결과 리포트
-- 사용자와 채용공고 조합별 하나의 분석 결과 저장
-- =============================================

CREATE TABLE analysis_reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    match_score INT,
    reason TEXT,
    strengths TEXT,
    weaknesses TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_analysis_report_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_analysis_report_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_analysis_report_user_job
        UNIQUE (user_id, job_posting_id),

    CONSTRAINT ck_analysis_report_match_score
        CHECK (
            match_score IS NULL
            OR match_score BETWEEN 0 AND 100
        )
);


-- =============================================
-- 2. 학습 로드맵
-- 분석 리포트를 기반으로 사용자별 학습 계획 저장
-- =============================================

CREATE TABLE roadmaps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    total_estimated_minutes INT,
    progress_rate NUMERIC(5, 2),
    estimated_end_date DATE,
    last_replanned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_roadmap_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_roadmap_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_roadmap_report
        FOREIGN KEY (report_id)
        REFERENCES analysis_reports(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_roadmap_title
        CHECK (BTRIM(title) <> ''),

    CONSTRAINT ck_roadmap_total_minutes
        CHECK (
            total_estimated_minutes IS NULL
            OR total_estimated_minutes >= 0
        ),

    CONSTRAINT ck_roadmap_progress_rate
        CHECK (
            progress_rate IS NULL
            OR progress_rate BETWEEN 0 AND 100
        )
);


-- =============================================
-- 3. 로드맵별 학습 대상 스킬
-- 하나의 로드맵에서 어떤 스킬을 어느 수준까지
-- 학습해야 하는지 저장
-- =============================================

CREATE TABLE roadmap_skills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roadmap_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    current_level VARCHAR(20),
    target_level VARCHAR(20),
    importance INT,
    priority INT,
    estimated_minutes INT,
    progress_rate NUMERIC(5, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_roadmap_skill_roadmap
        FOREIGN KEY (roadmap_id)
        REFERENCES roadmaps(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_roadmap_skill_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_roadmap_skill
        UNIQUE (roadmap_id, skill_id),

    CONSTRAINT ck_roadmap_skill_importance
        CHECK (
            importance IS NULL
            OR importance >= 0
        ),

    CONSTRAINT ck_roadmap_skill_priority
        CHECK (
            priority IS NULL
            OR priority >= 1
        ),

    CONSTRAINT ck_roadmap_skill_estimated_minutes
        CHECK (
            estimated_minutes IS NULL
            OR estimated_minutes >= 0
        ),

    CONSTRAINT ck_roadmap_skill_progress_rate
        CHECK (
            progress_rate IS NULL
            OR progress_rate BETWEEN 0 AND 100
        )
);


-- =============================================
-- 4. 학습 마일스톤
-- 사용자와 스킬 기준의 세부 학습 목표
-- =============================================

CREATE TABLE milestones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    completion_criteria TEXT,
    difficulty VARCHAR(50),
    estimated_minutes INT,
    status VARCHAR(50),
    progress_rate NUMERIC(5, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_milestone_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_milestone_skill
        FOREIGN KEY (skill_id)
        REFERENCES skills(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_milestone_title
        CHECK (BTRIM(title) <> ''),

    CONSTRAINT ck_milestone_estimated_minutes
        CHECK (
            estimated_minutes IS NULL
            OR estimated_minutes >= 0
        ),

    CONSTRAINT ck_milestone_progress_rate
        CHECK (
            progress_rate IS NULL
            OR progress_rate BETWEEN 0 AND 100
        )
);


-- =============================================
-- 5. 로드맵 스킬과 마일스톤 연결
-- 하나의 스킬 학습 계획에 여러 마일스톤 연결
-- =============================================

CREATE TABLE roadmap_milestones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roadmap_skill_id BIGINT NOT NULL,
    milestone_id BIGINT NOT NULL,
    learning_order INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_roadmap_milestone_roadmap_skill
        FOREIGN KEY (roadmap_skill_id)
        REFERENCES roadmap_skills(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_roadmap_milestone_milestone
        FOREIGN KEY (milestone_id)
        REFERENCES milestones(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_roadmap_milestone
        UNIQUE (roadmap_skill_id, milestone_id),

    CONSTRAINT ck_roadmap_milestone_learning_order
        CHECK (
            learning_order IS NULL
            OR learning_order >= 1
        )
);


-- =============================================
-- 6. 학습 리소스
-- 강의, 문서, 영상, 문제 등의 외부 학습 자료
-- =============================================

CREATE TABLE learning_resources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(100),
    external_id VARCHAR(100),
    resource_type VARCHAR(50),
    title VARCHAR(255),
    description TEXT,
    url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    duration_minutes INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_learning_resource_duration
        CHECK (
            duration_minutes IS NULL
            OR duration_minutes >= 0
        )
);


-- =============================================
-- 7. 마일스톤별 추천 학습 자료
-- =============================================

CREATE TABLE resource_recommendations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    milestone_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    rank_order INT,
    recommendation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_resource_recommendation_milestone
        FOREIGN KEY (milestone_id)
        REFERENCES milestones(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_resource_recommendation_resource
        FOREIGN KEY (resource_id)
        REFERENCES learning_resources(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_resource_recommendation
        UNIQUE (milestone_id, resource_id),

    CONSTRAINT ck_resource_recommendation_rank
        CHECK (
            rank_order IS NULL
            OR rank_order >= 1
        )
);


-- =============================================
-- 8. 학습 진행 이력
-- 진행률 변경 내용을 누적 저장
-- =============================================

CREATE TABLE learning_progresses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    milestone_id BIGINT NOT NULL,
    previous_progress NUMERIC(5, 2),
    current_progress NUMERIC(5, 2),
    studied_minutes INT,
    note TEXT,
    recorded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_learning_progress_milestone
        FOREIGN KEY (milestone_id)
        REFERENCES milestones(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_learning_progress_previous
        CHECK (
            previous_progress IS NULL
            OR previous_progress BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_learning_progress_current
        CHECK (
            current_progress IS NULL
            OR current_progress BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_learning_progress_studied_minutes
        CHECK (
            studied_minutes IS NULL
            OR studied_minutes >= 0
        )
);


-- =============================================
-- 9. 일반 조회용 인덱스
-- =============================================

-- 분석 리포트
-- (user_id, job_posting_id)는 UNIQUE 제약으로 인덱스 자동 생성
CREATE INDEX idx_analysis_reports_job_posting_id
    ON analysis_reports(job_posting_id);

CREATE INDEX idx_analysis_reports_created_at
    ON analysis_reports(created_at);


-- 로드맵
CREATE INDEX idx_roadmaps_user_id
    ON roadmaps(user_id);

CREATE INDEX idx_roadmaps_job_posting_id
    ON roadmaps(job_posting_id);

CREATE INDEX idx_roadmaps_report_id
    ON roadmaps(report_id);

CREATE INDEX idx_roadmaps_status
    ON roadmaps(status);

CREATE INDEX idx_roadmaps_user_status
    ON roadmaps(user_id, status);


-- 로드맵 스킬
CREATE INDEX idx_roadmap_skills_roadmap_id
    ON roadmap_skills(roadmap_id);

CREATE INDEX idx_roadmap_skills_skill_id
    ON roadmap_skills(skill_id);

CREATE INDEX idx_roadmap_skills_priority
    ON roadmap_skills(roadmap_id, priority);


-- 마일스톤
CREATE INDEX idx_milestones_user_id
    ON milestones(user_id);

CREATE INDEX idx_milestones_skill_id
    ON milestones(skill_id);

CREATE INDEX idx_milestones_status
    ON milestones(status);

CREATE INDEX idx_milestones_user_status
    ON milestones(user_id, status);


-- 로드맵 마일스톤 연결
CREATE INDEX idx_roadmap_milestones_roadmap_skill_id
    ON roadmap_milestones(roadmap_skill_id);

CREATE INDEX idx_roadmap_milestones_milestone_id
    ON roadmap_milestones(milestone_id);

CREATE INDEX idx_roadmap_milestones_learning_order
    ON roadmap_milestones(roadmap_skill_id, learning_order);


-- 학습 리소스
CREATE INDEX idx_learning_resources_provider
    ON learning_resources(provider);

CREATE INDEX idx_learning_resources_resource_type
    ON learning_resources(resource_type);

CREATE INDEX idx_learning_resources_external_id
    ON learning_resources(external_id);


-- 리소스 추천
CREATE INDEX idx_resource_recommendations_milestone_id
    ON resource_recommendations(milestone_id);

CREATE INDEX idx_resource_recommendations_resource_id
    ON resource_recommendations(resource_id);

CREATE INDEX idx_resource_recommendations_rank
    ON resource_recommendations(milestone_id, rank_order);


-- 학습 진행 이력
CREATE INDEX idx_learning_progresses_milestone_id
    ON learning_progresses(milestone_id);

CREATE INDEX idx_learning_progresses_recorded_at
    ON learning_progresses(recorded_at);

CREATE INDEX idx_learning_progresses_milestone_recorded
    ON learning_progresses(milestone_id, recorded_at DESC);


-- =============================================
-- 10. updated_at 자동 갱신 함수
-- 기존 마이그레이션에서 생성했어도 재사용 가능
-- =============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


-- =============================================
-- 11. updated_at 자동 갱신 트리거
-- =============================================

CREATE TRIGGER trg_roadmaps_updated_at
    BEFORE UPDATE ON roadmaps
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_roadmap_skills_updated_at
    BEFORE UPDATE ON roadmap_skills
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_milestones_updated_at
    BEFORE UPDATE ON milestones
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


CREATE TRIGGER trg_learning_resources_updated_at
    BEFORE UPDATE ON learning_resources
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

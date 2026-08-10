-- ============================================================================
-- 변경 없는 사용자 문서에 대해 스킬 LLM 추출을 반복하지 않기 위한 마지막 성공 상태입니다.
-- 실행 이력을 누적하는 테이블이 아니라 사용자별 최신 상태 한 건만 유지합니다.
-- ============================================================================

CREATE TABLE user_skill_analysis_states (
    user_id BIGINT PRIMARY KEY,
    document_hash CHAR(64) NOT NULL,
    pipeline_hash CHAR(64) NOT NULL,
    processed_chunk_count INTEGER NOT NULL,
    skill_count INTEGER NOT NULL,
    unmapped_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_skill_analysis_state_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_skill_analysis_state_document_hash
        CHECK (document_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_skill_analysis_state_pipeline_hash
        CHECK (pipeline_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_skill_analysis_state_processed_chunks
        CHECK (processed_chunk_count > 0),
    CONSTRAINT ck_user_skill_analysis_state_skill_count
        CHECK (skill_count > 0),
    CONSTRAINT ck_user_skill_analysis_state_unmapped_count
        CHECK (unmapped_count >= 0)
);

CREATE TRIGGER trg_user_skill_analysis_states_updated_at
    BEFORE UPDATE ON user_skill_analysis_states
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

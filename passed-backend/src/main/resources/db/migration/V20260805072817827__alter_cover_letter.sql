-- =============================================
-- 1. 마이그레이션 사전조건
--
-- 기존 피드백은 기본 자기소개서(cover_letters)를 참조하지만,
-- 새 구조에서는 회사별 자기소개서(cover_letters_company)를 참조합니다.
-- 기존 행을 새 회사별 자기소개서로 자동 매핑할 기준이 없으므로,
-- 데이터가 있는 환경에서는 임의 삭제하지 않고 마이그레이션을 중단합니다.
-- =============================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cover_letter_item_feedbacks)
       OR EXISTS (SELECT 1 FROM cover_letter_feedbacks) THEN
        RAISE EXCEPTION
            '기존 자기소개서 피드백 데이터가 있어 회사별 자기소개서 구조로 자동 변경할 수 없습니다.';
    END IF;
END;
$$;


-- =============================================
-- 2. 회사·채용공고별 자기소개서
--
-- 사용자는 여러 회사별 자기소개서를 가질 수 있으며,
-- 하나의 채용공고에는 하나의 자기소개서만 작성할 수 있습니다.
-- 기업은 job_postings.company_id를 통해 조회합니다.
-- =============================================

CREATE TABLE cover_letters_company (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letters_company_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_cover_letters_company_job_posting
        FOREIGN KEY (job_posting_id)
        REFERENCES job_postings(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_cover_letters_company_user_job_posting
        UNIQUE (user_id, job_posting_id),

    CONSTRAINT ck_cover_letters_company_title
        CHECK (BTRIM(title) <> '')
);


-- =============================================
-- 3. 회사별 자기소개서 문항과 답변
--
-- 회사별 문항은 고정 질문 마스터와 별개이므로 질문 문구를 직접 보관합니다.
-- =============================================

CREATE TABLE cover_letters_company_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cover_letter_company_id BIGINT NOT NULL,
    question_text VARCHAR(1000) NOT NULL,
    answer TEXT,
    character_limit INT,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cover_letters_company_item_cover_letter
        FOREIGN KEY (cover_letter_company_id)
        REFERENCES cover_letters_company(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_cover_letters_company_item_order
        UNIQUE (cover_letter_company_id, display_order),

    CONSTRAINT ck_cover_letters_company_item_question
        CHECK (BTRIM(question_text) <> ''),

    CONSTRAINT ck_cover_letters_company_item_character_limit
        CHECK (
            character_limit IS NULL
            OR character_limit > 0
        ),

    CONSTRAINT ck_cover_letters_company_item_display_order
        CHECK (display_order >= 1)
);


-- =============================================
-- 4. 문항별 피드백을 회사별 자기소개서 문항과 1:1로 변경
--
-- 전체 피드백은 회사별 자기소개서를 통해 유일하게 찾을 수 있으므로
-- feedback_id를 중복 저장하지 않습니다.
-- score는 숫자가 아니라 '충분', '미흡', '부족' 중 하나입니다.
-- =============================================

DROP INDEX idx_cover_letter_item_feedbacks_feedback_id;
DROP INDEX idx_cover_letter_item_feedbacks_item_id;

ALTER TABLE cover_letter_item_feedbacks
    DROP CONSTRAINT fk_cover_letter_item_feedback_feedback,
    DROP CONSTRAINT fk_cover_letter_item_feedback_item,
    DROP CONSTRAINT uk_cover_letter_item_feedback,
    DROP CONSTRAINT ck_cover_letter_item_feedback_score,
    DROP COLUMN feedback_id,
    DROP COLUMN cover_letter_item_id,
    ADD COLUMN cover_letter_company_item_id BIGINT NOT NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN score TYPE VARCHAR(10)
        USING score::VARCHAR(10),
    ADD CONSTRAINT fk_cover_letter_item_feedback_company_item
        FOREIGN KEY (cover_letter_company_item_id)
        REFERENCES cover_letters_company_items(id)
        ON DELETE CASCADE,
    ADD CONSTRAINT uk_cover_letter_item_feedback_company_item
        UNIQUE (cover_letter_company_item_id),
    ADD CONSTRAINT ck_cover_letter_item_feedback_score
        CHECK (
            score IS NULL
            OR score IN ('충분', '미흡', '부족')
        );


-- =============================================
-- 5. 전체 피드백을 회사별 자기소개서와 1:1로 변경
--
-- 채용공고는 cover_letters_company.job_posting_id를 통해 조회하므로
-- cover_letter_feedbacks에는 중복 저장하지 않습니다.
-- overall_score는 숫자가 아니라 '충분', '미흡', '부족' 중 하나입니다.
-- =============================================

DROP INDEX idx_cover_letter_feedbacks_cover_letter_id;
DROP INDEX idx_cover_letter_feedbacks_job_posting_id;

ALTER TABLE cover_letter_feedbacks
    DROP CONSTRAINT fk_cover_letter_feedback_cover_letter,
    DROP CONSTRAINT fk_cover_letter_feedback_job_posting,
    DROP CONSTRAINT ck_cover_letter_feedback_overall_score,
    DROP COLUMN cover_letter_id,
    DROP COLUMN job_posting_id,
    ADD COLUMN cover_letter_company_id BIGINT NOT NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN overall_score TYPE VARCHAR(10)
        USING overall_score::VARCHAR(10),
    ADD CONSTRAINT fk_cover_letter_feedback_company
        FOREIGN KEY (cover_letter_company_id)
        REFERENCES cover_letters_company(id)
        ON DELETE CASCADE,
    ADD CONSTRAINT uk_cover_letter_feedback_company
        UNIQUE (cover_letter_company_id),
    ADD CONSTRAINT ck_cover_letter_feedback_overall_score
        CHECK (
            overall_score IS NULL
            OR overall_score IN ('충분', '미흡', '부족')
        );


-- =============================================
-- 6. 조회 인덱스
--
-- 사용자별 자기소개서와 자기소개서별 문항 조회는 각각
-- UNIQUE (user_id, job_posting_id),
-- UNIQUE (cover_letter_company_id, display_order)의 선두 컬럼을 사용합니다.
-- 피드백 FK도 UNIQUE 제약이 인덱스 역할을 함께 수행합니다.
-- =============================================

CREATE INDEX idx_cover_letters_company_job_posting_id
    ON cover_letters_company(job_posting_id);


-- =============================================
-- 7. updated_at 자동 갱신 트리거
-- =============================================

CREATE TRIGGER trg_cover_letters_company_updated_at
    BEFORE UPDATE ON cover_letters_company
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_cover_letters_company_items_updated_at
    BEFORE UPDATE ON cover_letters_company_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_cover_letter_feedbacks_updated_at
    BEFORE UPDATE ON cover_letter_feedbacks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_cover_letter_item_feedbacks_updated_at
    BEFORE UPDATE ON cover_letter_item_feedbacks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 자기소개서 목록에서 직접 입력한 채용공고 정보를 공용 job_postings와 분리해 보관합니다.
CREATE TABLE cover_letter_manual_job_postings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    posting_title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    job_role_name VARCHAR(255) NOT NULL,
    position_detail TEXT,
    career_type VARCHAR(50),
    hire_type VARCHAR(255),
    main_duty TEXT,
    qualification TEXT,
    preference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cover_letter_manual_posting_title CHECK (BTRIM(posting_title) <> ''),
    CONSTRAINT ck_cover_letter_manual_company_name CHECK (BTRIM(company_name) <> ''),
    CONSTRAINT ck_cover_letter_manual_job_role_name CHECK (BTRIM(job_role_name) <> '')
);

ALTER TABLE cover_letters_company
    ALTER COLUMN job_posting_id DROP NOT NULL,
    ADD COLUMN manual_job_posting_id BIGINT;

ALTER TABLE cover_letters_company
    ADD CONSTRAINT fk_cover_letters_company_manual_job_posting
        FOREIGN KEY (manual_job_posting_id)
        REFERENCES cover_letter_manual_job_postings(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT uk_cover_letters_company_manual_job_posting
        UNIQUE (manual_job_posting_id),
    ADD CONSTRAINT ck_cover_letters_company_exactly_one_posting
        CHECK ((job_posting_id IS NOT NULL) <> (manual_job_posting_id IS NOT NULL));

CREATE INDEX idx_cover_letters_company_user_updated_at
    ON cover_letters_company(user_id, updated_at DESC, id DESC);

CREATE TRIGGER trg_cover_letter_manual_job_postings_updated_at
    BEFORE UPDATE ON cover_letter_manual_job_postings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 애플리케이션 밖에서 부모가 삭제되어도 직접 입력 공고가 고아 데이터로 남지 않게 합니다.
CREATE FUNCTION delete_cover_letter_manual_job_posting()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.manual_job_posting_id IS NOT NULL THEN
        DELETE FROM cover_letter_manual_job_postings
        WHERE id = OLD.manual_job_posting_id;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_delete_cover_letter_manual_job_posting
    AFTER DELETE ON cover_letters_company
    FOR EACH ROW
    EXECUTE FUNCTION delete_cover_letter_manual_job_posting();

-- 회사명이 없는 직접 입력 공고도 허용하고 제목은 애플리케이션에서 사용자별 기본값을 생성합니다.
ALTER TABLE cover_letter_manual_job_postings
    ALTER COLUMN company_name DROP NOT NULL,
    DROP CONSTRAINT ck_cover_letter_manual_company_name;

-- 연결된 직접 입력 공고 FK가 교체되면 이전 스냅샷도 고아로 남지 않게 정리합니다.
CREATE OR REPLACE FUNCTION delete_cover_letter_manual_job_posting()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.manual_job_posting_id IS NOT NULL THEN
            DELETE FROM cover_letter_manual_job_postings
            WHERE id = OLD.manual_job_posting_id;
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.manual_job_posting_id IS NOT NULL
       AND OLD.manual_job_posting_id IS DISTINCT FROM NEW.manual_job_posting_id THEN
        DELETE FROM cover_letter_manual_job_postings
        WHERE id = OLD.manual_job_posting_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_cover_letter_manual_job_posting
    AFTER UPDATE OF manual_job_posting_id ON cover_letters_company
    FOR EACH ROW
    WHEN (OLD.manual_job_posting_id IS DISTINCT FROM NEW.manual_job_posting_id)
    EXECUTE FUNCTION delete_cover_letter_manual_job_posting();

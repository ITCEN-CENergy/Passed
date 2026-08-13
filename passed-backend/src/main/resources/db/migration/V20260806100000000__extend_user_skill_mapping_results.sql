-- Q. 왜 기존 user_skill_evidences 컬럼만으로는 부족한가요?
-- A. 청크 ID만 저장하면 청크 안의 어떤 문구와 level을 근거로 삼았는지 복원할 수
--    없습니다. AI가 만든 매핑 결과를 검증할 수 있도록 근거 문구와 신뢰도를 보존합니다.
ALTER TABLE user_skills
    ADD COLUMN mapping_confidence NUMERIC(4, 3),
    ADD COLUMN level_confidence NUMERIC(4, 3),
    ADD CONSTRAINT ck_user_skills_mapping_confidence
        CHECK (mapping_confidence IS NULL OR mapping_confidence BETWEEN 0 AND 1),
    ADD CONSTRAINT ck_user_skills_level_confidence
        CHECK (level_confidence IS NULL OR level_confidence BETWEEN 0 AND 1);

ALTER TABLE user_skill_evidences
    ADD COLUMN evidence_text VARCHAR(500),
    ADD COLUMN extracted_level SMALLINT,
    ADD COLUMN mapping_confidence NUMERIC(4, 3);

-- 기존 개발 데이터도 새 NOT NULL 계약을 만족하도록 보수적인 기본값으로 보강합니다.
UPDATE user_skill_evidences
SET evidence_text = extracted_name,
    extracted_level = 1,
    mapping_confidence = CASE mapping_method
        WHEN 'EXACT' THEN 1.000
        WHEN 'KEYWORD' THEN 0.950
        WHEN 'EMBEDDING' THEN COALESCE(mapping_similarity, 0.750)
        ELSE 0.500
    END
WHERE evidence_text IS NULL
   OR extracted_level IS NULL
   OR mapping_confidence IS NULL;

ALTER TABLE user_skill_evidences
    ALTER COLUMN evidence_text SET NOT NULL,
    ALTER COLUMN extracted_level SET NOT NULL,
    ALTER COLUMN mapping_confidence SET NOT NULL,
    ADD CONSTRAINT ck_user_skill_evidence_text
        CHECK (BTRIM(evidence_text) <> ''),
    ADD CONSTRAINT ck_user_skill_evidence_level
        CHECK (extracted_level BETWEEN 1 AND 3),
    ADD CONSTRAINT ck_user_skill_evidence_mapping_confidence
        CHECK (mapping_confidence BETWEEN 0 AND 1);

-- Q. KEYWORD를 바로 제거하지 않는 이유는 무엇인가요?
-- A. 기존 공고·사용자 근거가 이미 KEYWORD를 사용했을 수 있습니다. 과거 데이터는
--    읽을 수 있게 유지하고 신규 AI 파이프라인은 NORMALIZED와 ALIAS를 구분합니다.
ALTER TABLE user_skill_evidences
    DROP CONSTRAINT ck_user_skill_evidence_mapping_method,
    ADD CONSTRAINT ck_user_skill_evidence_mapping_method
        CHECK (
            mapping_method IN (
                'EXACT',
                'NORMALIZED',
                'ALIAS',
                'KEYWORD',
                'EMBEDDING'
            )
        );

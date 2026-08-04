CREATE TYPE company_size_enum AS ENUM (
    'LARGE_ENTERPRISE',
    'PUBLIC_INSTITUTION',
    'STARTUP',
    'MID_SIZED_ENTERPRISE',
    'SMALL_AND_MEDIUM_ENTERPRISE'
);

ALTER TABLE companies
    ALTER COLUMN company_size DROP DEFAULT;

ALTER TABLE companies
    ALTER COLUMN company_size TYPE company_size_enum
    USING (
        CASE company_size::text
            WHEN '대기업'   THEN 'LARGE_ENTERPRISE'
            WHEN '공공기관' THEN 'PUBLIC_INSTITUTION'
            WHEN '스타트업' THEN 'STARTUP'
            WHEN '중견기업' THEN 'MID_SIZED_ENTERPRISE'
            WHEN '중소기업' THEN 'SMALL_AND_MEDIUM_ENTERPRISE'
            ELSE company_size::text
        END
    )::company_size_enum;

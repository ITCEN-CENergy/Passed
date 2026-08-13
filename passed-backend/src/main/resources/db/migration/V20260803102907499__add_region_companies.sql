ALTER TABLE companies
ADD COLUMN region varchar(100);

COMMENT ON COLUMN companies.region IS '기업 소재 지역';
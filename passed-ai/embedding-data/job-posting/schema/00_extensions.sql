-- 청크 임베딩을 PostgreSQL vector(1536) 컬럼에 저장하기 위한 pgvector 확장.
-- CREATE EXTENSION 권한이 없는 계정에서는 DBA가 한 번만 실행해야 한다.
CREATE EXTENSION IF NOT EXISTS vector;

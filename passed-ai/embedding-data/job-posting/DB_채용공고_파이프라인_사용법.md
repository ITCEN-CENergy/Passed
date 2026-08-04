# DB 채용공고 적재·청크·임베딩 사용법

## 1. 실행 구조

```text
CSV
  -> job_postings UPSERT
  -> 원문 정규화·청킹
  -> job_posting_chunks 동기화
  -> 별도 임베딩 작업자
```

생성형 LLM을 이용한 `tech_stacks`, `benefits` 추출은 제거되었다. CSV 적재와
청크 생성에는 OpenAI API를 호출하지 않는다.

현재 DB는 Docker가 노출한 `localhost:5433/edu`이며, 컨테이너 내부 포트는
5432다. `job_postings`와 `job_posting_chunks`는 backend Flyway가 소유하며
Python은 실행 전에 필수 컬럼과 `vector(1536)` 계약만 검증한다.

## 2. 환경 설정

```powershell
cd C:\Users\user\work\passed\Passed\passed-ai
Copy-Item .env.example .env
uv sync
```

```dotenv
DATABASE_URL=postgresql://edu:<비밀번호>@localhost:5433/edu
DATABASE_CONNECT_TIMEOUT_SECONDS=10

# 임베딩 실행 시에만 필요
OPENAI_API_KEY=<OpenAI API 키>
EMBEDDING_MODEL=openai/text-embedding-3-small
EMBEDDING_DIMENSION=1536
```

## 3. CSV 구조

헤더는 다음 순서를 권장한다.

```text
job_posting_id
title
company_id
job_role_id
start_ymd
end_ymd
headcount
career_type
hire_type
region
edu_level
position_detail
main_duty
qualification
preference
disqualify_reason
process
```

멀티라인 텍스트는 CSV 따옴표로 감싸야 한다.

```csv
job_posting_id,title,company_id,job_role_id,start_ymd,end_ymd,headcount,career_type,hire_type,region,edu_level,position_detail,main_duty,qualification,preference,disqualify_reason,process
1,경영·비즈니스기획 담당자 모집,39,1,20260204,20260221,6,경력 2년 이상,계약직,인천광역시,전문학사 이상,"포지션 상세","- 주요 업무","- 지원 자격","- 우대사항","- 결격 사유","서류전형 > 실무면접 > 최종면접"
```

검증 규칙:

- `job_posting_id`, `title`, `company_id`, `job_role_id` 필수
- 날짜는 `YYYYMMDD`
- `start_ymd <= end_ymd`
- `headcount`는 NULL 또는 1 이상
- CSV의 회사·직무 ID가 참조 테이블에 존재해야 함

## 4. 청크 생성 규칙

```text
position_detail   -> POSITION_DETAIL  -> 문단 우선, 최대 400토큰/50토큰 중첩
main_duty         -> MAIN_TASK        -> 줄 단위
qualification     -> REQUIREMENT      -> 줄 단위
preference        -> PREFERENCE       -> 줄 단위
disqualify_reason -> DISQUALIFICATION -> 줄 단위
process           -> PROCESS          -> 원문 전체 한 청크
```

- 빈 필드는 청크를 생성하지 않는다.
- CSV에 없는 `BENEFIT` 청크는 생성하지 않는다.
- `TECH_STACK`은 현재 청크 스키마의 허용값이 아니며 생성하지 않는다.
- 동일 키와 동일 `content_hash`는 기존 임베딩을 유지한다.
- 텍스트가 바뀌면 임베딩을 NULL/PENDING으로 초기화한다.
- 원문에서 사라진 청크는 삭제한다.

## 5. 참조 데이터 확인

```sql
SELECT COUNT(*), MIN(id), MAX(id) FROM companies;
SELECT COUNT(*), MIN(id), MAX(id) FROM job_roles;
```

필요한 참조 ID가 없다면 로더는 INSERT 전에 중단한다.

## 6. CSV 적재

```powershell
cd C:\Users\user\work\passed\Passed\passed-ai\embedding-data\job-posting

uv run python -m job_posting_pipeline.run_loader `
  data\job_postings.csv
```

여러 파일도 한 번에 전달할 수 있다.

```powershell
uv run python -m job_posting_pipeline.run_loader `
  data\part1.csv `
  data\part2.csv
```

동일 CSV를 다시 실행하면 `job_postings.id` 기준 UPSERT와 청크 해시 비교를
수행한다.

## 7. 임베딩

한 배치만 시험:

```powershell
uv run python -m job_posting_pipeline.run_embedding `
  --max-iterations 1 `
  --batch-size 20
```

전체 처리:

```powershell
uv run python -m job_posting_pipeline.run_embedding
```

매칭 임베딩에서는 `PROCESS`, `DISQUALIFICATION`, `BENEFIT`을 제외한다.
성공한 행에는 벡터와 함께 `embedding_model`,
`embedding_status='COMPLETED'`, `embedding_updated_at`이 기록된다.

## 8. 검증 SQL

```sql
SELECT COUNT(*) FROM job_postings;

SELECT
    COUNT(*) AS chunk_count,
    COUNT(DISTINCT job_posting_id) AS posting_count
FROM job_posting_chunks;

SELECT source_type, embedding_status, COUNT(*)
FROM job_posting_chunks
GROUP BY source_type, embedding_status
ORDER BY source_type, embedding_status;

SELECT COUNT(*) AS invalid_empty_chunks
FROM job_posting_chunks
WHERE btrim(chunk_content) = '';

SELECT COUNT(*) AS invalid_dimension
FROM job_posting_chunks
WHERE embedding IS NOT NULL
  AND vector_dims(embedding) <> 1536;
```

## 9. 로그와 오류

```text
logs/loader.log
logs/embedding.log
```

- `companies.id 누락`: CSV의 회사 기준정보가 없음
- `job_roles.id 누락`: CSV의 직무 기준정보가 없음
- `DB 계약 검증 실패`: 연결한 DB가 현재 Flyway 구조와 다름
- 임베딩 `Connection error`: OpenAI 네트워크 또는 API 키 문제

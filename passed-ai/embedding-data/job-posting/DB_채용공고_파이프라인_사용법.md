# DB 채용공고 적재·청크·임베딩 사용법

## 1. 구성과 DB 기준

이 작업자는 기존 FastAPI와 분리된 CLI 프로그램이다.

```text
CSV
  -> job_postings UPSERT
  -> 원문 정규화·청킹
  -> job_posting_chunks 동기화
  -> 별도 임베딩 작업자
```

현재 기준 DB는 Docker가 노출한 `localhost:5433/edu`이다. 컨테이너 내부
PostgreSQL 포트는 5432이며, 호스트의 5433 포트가 이 포트로 전달된다.

`industries`, `job_roles`, `companies`, `job_postings`,
`job_posting_chunks`, `skills`, `job_posting_skills`는
`passed-backend`의 Flyway V1~V7이 소유한다. Python 파이프라인은 이 스키마를
변경하는 주체가 아니며, 실행 시 필수 컬럼과 `vector(1536)` 계약을 검증한다.

파이프라인이 추가로 사용하는 테이블은 LLM 결과 캐시인
`job_posting_extraction_meta`뿐이다.

## 2. 현재 청크 DB 계약

`job_posting_chunks.source_type` 허용값:

```text
POSITION_DETAIL
MAIN_TASK
REQUIREMENT
PREFERENCE
BENEFIT
PROCESS
DISQUALIFICATION
```

- 빈 `chunk_content`는 저장하지 않는다.
- `TECH_STACK`과 `ETC`는 Flyway V3의 청크 허용값이 아니다.
- 기술 추출 결과는 캐시에 남지만, `job_posting_skills`에 연결하려면 별도의
  스킬 마스터 매핑 작업이 필요하다.
- 매칭 임베딩은 `PROCESS`, `DISQUALIFICATION`, `BENEFIT`을 제외한다.
- 임베딩 완료 행은 `embedding_status='COMPLETED'`와
  `embedding_model`, `embedding_updated_at`을 함께 기록한다.

## 3. 환경 준비

PowerShell:

```powershell
cd C:\Users\user\work\passed\Passed\passed-ai
Copy-Item .env.example .env
uv sync
```

`.env`에서 실제 값을 입력한다.

```dotenv
DATABASE_URL=postgresql://edu:<비밀번호>@localhost:5433/edu
DATABASE_CONNECT_TIMEOUT_SECONDS=10
OPENAI_API_KEY=<OpenAI API 키>
```

OpenAI 추출 없이 원문 청크만 만들려면 다음과 같이 설정한다.

```dotenv
EXTRACT_WITH_LLM=false
```

이 설정은 외부 네트워크가 없는 환경에서 유용하다.

## 4. 참조 데이터 확인

CSV 적재 전에 `companies.id`와 `job_roles.id`가 존재해야 한다.

```sql
SELECT COUNT(*), MIN(id), MAX(id) FROM companies;
SELECT COUNT(*), MIN(id), MAX(id) FROM industries;
SELECT COUNT(*), MIN(id), MAX(id) FROM job_roles;
```

현재 제공 fixture 기준 예상값:

```text
companies: 160 / 0 / 159
industries: 21 / 1 / 21
job_roles: 239 / 1 / 239
```

개발 DB가 비어 있을 때만 다음 SQL을 검토 후 적용한다.

```text
schema/seed_industries_job_roles_from_excel.sql
schema/dev_seed_companies_0_159.sql
```

두 SQL은 `GENERATED ALWAYS AS IDENTITY` 테이블에 명시 ID를 넣기 위해
`OVERRIDING SYSTEM VALUE`를 사용한다.

## 5. CSV 적재와 청크 생성

작업 폴더로 이동한다.

```powershell
cd C:\Users\user\work\passed\Passed\passed-ai\embedding-data\job-posting
```

단일 CSV:

```powershell
uv run python -m job_posting_pipeline.run_loader `
  data/job_postings_role_id_1_61_610rows.csv
```

여러 CSV:

```powershell
uv run python -m job_posting_pipeline.run_loader `
  data/job_postings_role_id_1_61_610rows.csv `
  data/job_postings_123_184_620rows_new_schema_add_job_posting_id.csv `
  data/job_postings_185_239_excel_safe_add_job_posting_id.csv
```

구버전 `job_postings_role_id_1_61_610rows.csv`처럼
`job_posting_id`가 없더라도 모든 직무가 정확히 10건이면 다음 규칙으로
ID를 복원한다.

```text
(job_role_id - 1) * 10 + 직무 내 순번
```

일부 행에만 ID가 있거나 직무별 10건이 아니면 임의 ID를 만들지 않고
명확한 오류로 중단한다.

`job_postings.id`는 `GENERATED ALWAYS AS IDENTITY`이므로 로더는
`OVERRIDING SYSTEM VALUE`로 fixture ID를 보존한다. 같은 CSV를 다시 실행하면
ID 기준 UPSERT와 해시 기반 청크 동기화가 수행된다.

## 6. 임베딩 실행

먼저 한 배치만 확인:

```powershell
uv run python -m job_posting_pipeline.run_embedding `
  --max-iterations 1 `
  --batch-size 20
```

남은 매칭 청크 전체 처리:

```powershell
uv run python -m job_posting_pipeline.run_embedding
```

모델명이 달라진 기존 벡터도 새 모델로 다시 임베딩한다. API 요청 후
원문 해시가 바뀐 행은 저장하지 않아 오래된 벡터 덮어쓰기를 방지한다.

## 7. 결과 검증

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

SELECT
    COUNT(*) FILTER (
        WHERE source_type NOT IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT')
          AND (embedding IS NULL OR embedding_status <> 'COMPLETED')
    ) AS matching_pending,
    COUNT(*) FILTER (
        WHERE embedding IS NOT NULL
          AND vector_dims(embedding) <> 1536
    ) AS invalid_dimension
FROM job_posting_chunks;
```

## 8. 로그

```text
logs/loader.log
logs/embedding.log
```

진행률, 공고 ID, 청크 INSERT/UPDATE/DELETE 수, 임베딩 배치 범위와 실패 원인이
기록된다.

## 9. 자주 발생하는 오류

### `companies.id 누락` / `job_roles.id 누락`

CSV가 참조하는 기준정보가 DB에 없다. 4절의 조회 SQL로 확인하고 개발용
seed를 먼저 적용한다.

### `Connection error` during extraction

OpenAI 네트워크 연결 또는 API 키 문제다. 네트워크가 없는 환경에서는
`EXTRACT_WITH_LLM=false`로 원문 청크만 생성하고, 추출은 네트워크 환경에서
다시 실행한다.

### DB 계약 검증 실패

Flyway가 적용되지 않았거나 DB가 다른 버전이다. `flyway_schema_history`와
`passed-backend/src/main/resources/db/migration`을 먼저 확인하고 Python
코드에서 임의로 백엔드 테이블을 변경하지 않는다.

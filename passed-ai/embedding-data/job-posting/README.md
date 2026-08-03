# 채용공고 청크 생성·임베딩 파이프라인

`passed-ai/app` 의 FastAPI 서버와는 **분리된** 독립 파이썬 패키지
(`job_posting_pipeline`)다.
옵시디언 설계문 "채용공고 청크 생성과 임베딩 부분 구현 계획"을 코드로 구현한다.
원본·정형 데이터는 `job_postings` 기준, 청크는 파생 데이터로 취급한다.

## 처리 흐름

```text
CSV 행 읽기 -> 값 검증·정규화 -> company_id 결정적 배정 -> job_postings UPSERT
-> 기본 텍스트 분리 + LLM 구조화 추출(tech_stack/benefit) -> 목록/토큰 청킹
-> content_hash 비교(신규/변경/유지/삭제) -> 별도 임베딩 작업자 -> text-embedding-3-small
```

## 패키지 구조

| 파일 | 역할 |
|---|---|
| `job_posting_pipeline/config.py` | 환경설정(`passed-ai/.env`), 계획서 13절 값 |
| `job_posting_pipeline/db.py` | psycopg 연결·스키마 초기화 |
| `schema/00_extensions.sql` | 로컬 bootstrap 참고용 pgvector 확장 |
| `schema/01_job_posting_chunks.sql` | 로컬 bootstrap 참고용 Flyway V3 호환 계약 |
| `schema/02_extraction_meta.sql` | LLM 추출 입력 해시 캐시(권장) |
| `job_posting_pipeline/models.py` | `SourceType`, 매칭 여부, `Chunk`/`ExtractedItem` |
| `job_posting_pipeline/normalize.py` | 공통 정규화, 지역/경력/고용형태/학력/기술스택 별칭 통합 |
| `job_posting_pipeline/company_assignment.py` | `SHA-256(job_posting_id + fixed_seed) % 160` 배정 |
| `job_posting_pipeline/csv_loader.py` | UTF-8/CP949 CSV 파싱·검증·UPSERT·시퀀스 보정 |
| `job_posting_pipeline/chunker.py` | 목록/서술/절차 분할·빈 청크·`content_hash` |
| `job_posting_pipeline/extraction.py` | LLM 구조화 추출(JSON 스키마), evidence 검증, 입력 해시 캐시 |
| `job_posting_pipeline/chunk_sync.py` | 공고 단위 트랜잭션 동기화(해시 기반 재사용·물리 삭제) |
| `job_posting_pipeline/embedding_worker.py` | 분리된 임베딩 배치 작업(차원 검증, 저장 전 해시 재검증, 백오프) |
| `job_posting_pipeline/queries.py` | 비매칭 source_type 제외·완료 임베딩 조회 |
| `job_posting_pipeline/run_loader.py` | CSV 적재 + 청크 동기화 진입점 |
| `job_posting_pipeline/run_embedding.py` | 임베딩 작업자 진입점 |

## 실행

```bash
cd passed-ai
cp .env.example .env  # 값 입력(DATABASE_URL, OPENAI_API_KEY)
uv sync
cd embedding-data/job-posting

# 1) CSV 적재 + 청크 생성/동기화
uv run python -m job_posting_pipeline.run_loader data/<csv파일>

# 2) 임베딩 작업자(별도 실행, 적재와 독립적 재시작 가능)
uv run python -m job_posting_pipeline.run_embedding

# 테스트
uv run python -m pytest tests -q
```

실행 로그는 콘솔에 실시간으로 표시되는 동시에 다음 파일에 저장된다.

```text
logs/loader.log
logs/embedding.log
```

각 로그는 10MB 단위로 회전하며 이전 파일을 최대 5개 보관한다. 다른 위치에
저장하려면 두 명령 모두 `--log-file <경로>`를 사용할 수 있다.

```bash
uv run python -m job_posting_pipeline.run_loader \
  --log-file logs/my-loader.log data/<csv파일>
```

## 주요 설계 원칙(계획서 요약)

- 임베딩 모델 `openai/text-embedding-3-small`, 차원 1536.
- `job_posting_chunks` 의 `embedding` 은 `vector(1536)`.
- DB에는 `use_for_matching` 컬럼이 없다. `PROCESS`, `DISQUALIFICATION`,
  `BENEFIT`은 조회 시 source_type 기준으로 제외한다.
- Flyway V3가 빈 청크를 금지하므로 빈 요소는 DB에 저장하지 않는다.
- `TECH_STACK`은 청크가 아니라 향후 `job_posting_skills` 매핑 대상이다.
- 변경된 청크만 `embedding=NULL` 로 재임베딩, 사라진 청크는 물리 삭제.
- 임베딩 생성은 적재 트랜잭션과 분리된 별도 작업자.

## 개발용 임시 회사 데이터

로컬 DB의 `companies` 테이블이 비어 있으면 CSV 적재 전에 다음 SQL을 수동으로
실행하여 `id` 0~159를 채울 수 있다.

```text
schema/dev_seed_companies_0_159.sql
```

이 SQL은 운영 DB용이 아니며 스키마 초기화 과정에서 자동 실행되지 않는다.
재실행해도 기존 회사 행은 수정하지 않는다. 실행 후 결과 조회에서
`company_count_0_159=160`, `min_company_id=0`, `max_company_id=159`인지 확인한다.

`job_postings_185_239` CSV를 적재할 때 `job_roles.id` 185~239가 없다면 다음
개발용 SQL을 수동으로 실행할 수 있다.

```text
schema/dev_seed_job_roles_185_239.sql
```

이 SQL은 필수 외래키를 만족시키기 위해 `industries.id=0`인 임시 산업과
`job_roles.id=185~239`인 임시 직무를 생성한다. 실제 산업·직무 기준정보가
준비되면 임시 데이터를 사용하지 말고 올바른 기준정보를 적재해야 한다.

실제 산업·직무 기준 엑셀 `산업과_직무_분류_(1).xlsx`를 기준으로 산업 21개와
직무 239개를 적재하려면 다음 SQL을 사용한다.

```text
schema/seed_industries_job_roles_from_excel.sql
```

이 SQL은 엑셀의 직무 ID 1~239를 그대로 사용한다. 동일 ID의 임시 직무가
있으면 실제 산업·직무명으로 갱신하고, 적재 결과 239개가 모두 원본과
일치하지 않으면 트랜잭션을 중단한다.

## 주의/확정 필요

- `job_postings`, `companies`, `job_roles` 는 기존(Spring) DB에 이미 존재한다고 가정한다. 실제 컬럼명이 설계와 다르면 `csv_loader._UPSERT_SQL`/`fetch_posting` 을 맞춰야 한다.
- 결정 필요했던 설정값(`EXTRACTION_MODEL`, `EMBEDDING_BATCH_SIZE`, `EMBEDDING_MAX_RETRIES`, `COMPANY_ASSIGNMENT_SEED`)은 기본값을 두었으니 운영 환경에서 검토 후 `.env` 로 확정.
- `OPENAI_API_KEY` 가 없으면 LLM 추출을 건너뛰고 tech_stack/benefit 빈 청크만 생성한다.
- 별도 DB 검증 없이 작성한 코드이므로 실제 DB에서 실행 전 스키마/제약·참조 데이터를 확인해야 한다.

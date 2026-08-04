# 채용공고 CSV 적재·청크·임베딩 파이프라인

기존 FastAPI와 분리된 독립 CLI 패키지다. 생성형 LLM 추출은 사용하지 않는다.

```text
CSV 검증·정규화
  -> job_postings UPSERT
  -> 원문 필드 청킹
  -> content_hash 기반 job_posting_chunks 동기화
  -> 별도 OpenAI 임베딩 작업자
```

## CSV 계약

```text
job_posting_id,title,company_id,job_role_id,start_ymd,end_ymd,headcount,
career_type,hire_type,region,edu_level,position_detail,main_duty,
qualification,preference,disqualify_reason,process
```

- `job_posting_id`, `title`, `company_id`, `job_role_id`는 필수다.
- 날짜는 `YYYYMMDD`, 모집 인원은 값이 있으면 1 이상이어야 한다.
- `hire_type`, `region`, `edu_level`은 DB 컬럼명과 동일한 헤더를 사용한다.
- 이전 fixture의 `hire_type_lst`, `region_lst`, `edu_level_lst`도 읽을 수 있다.
- 직무별 정확히 10건인 구버전 fixture만 누락 ID를 결정적으로 복원한다.

## 생성하는 청크

| CSV/DB 원문 | source_type | 분할 방식 |
|---|---|---|
| `position_detail` | `POSITION_DETAIL` | 문단·토큰 분할 |
| `main_duty` | `MAIN_TASK` | 줄 단위 |
| `qualification` | `REQUIREMENT` | 줄 단위 |
| `preference` | `PREFERENCE` | 줄 단위 |
| `disqualify_reason` | `DISQUALIFICATION` | 줄 단위 |
| `process` | `PROCESS` | 원문 한 건 |

CSV에 없는 `tech_stacks`와 `benefits`는 생성하지 않는다. 빈 원문도 청크로
저장하지 않는다.

## 실행

```powershell
cd C:\Users\user\work\passed\Passed\passed-ai
uv sync
cd embedding-data\job-posting

uv run python -m job_posting_pipeline.run_loader data\<파일>.csv
uv run python -m job_posting_pipeline.run_embedding
uv run python -m pytest tests -q
```

적재 작업은 OpenAI 키 없이 실행할 수 있다. `OPENAI_API_KEY`는
`run_embedding`에서만 필요하다.

상세 절차는
[DB 채용공고 파이프라인 사용법](./DB_채용공고_파이프라인_사용법.md)을 참고한다.

## 주요 파일

| 파일 | 역할 |
|---|---|
| `config.py` | DB·청킹·임베딩 설정 |
| `csv_loader.py` | CSV 검증·정규화·UPSERT |
| `chunker.py` | 원문 필드 청킹·해시 계산 |
| `chunk_sync.py` | 청크 INSERT/UPDATE/DELETE·벡터 재사용 |
| `embedding_worker.py` | OpenAI 임베딩 배치 |
| `run_loader.py` | CSV 적재와 청크 동기화 CLI |
| `run_embedding.py` | 임베딩 CLI |
| `db.py` | 5433 DB 연결과 Flyway 계약 검증 |

로그는 `logs/loader.log`, `logs/embedding.log`에 기록된다.

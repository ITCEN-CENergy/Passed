# 채용공고 청크 임베딩 실행

`job_posting_pipeline.run_embedding`은 CSV/청크 적재와 독립적으로 실행되는
배치 작업자다. 다음 조건을 만족하는 청크만 OpenAI Embeddings API로 보낸다.

```sql
embedding IS NULL
AND chunk_content <> ''
AND use_for_matching = true
```

## 설정

`passed-ai/.env`에서 다음 값을 사용한다.

```text
OPENAI_API_KEY=<OpenAI API key>
EMBEDDING_MODEL=openai/text-embedding-3-small
EMBEDDING_DIMENSION=1536
EMBEDDING_BATCH_SIZE=100
EMBEDDING_MAX_RETRIES=5
EMBEDDING_REQUEST_TIMEOUT_SECONDS=60
EMBEDDING_ONLY_MATCHING=true
```

코드는 모델명 앞의 `openai/`를 제거하고 API에는
`text-embedding-3-small`을 전달한다.

## 실행

처음에는 한 배치만 실행해 API 키, 모델, DB 저장을 확인한다.

```powershell
uv run python -m job_posting_pipeline.run_embedding --max-iterations 1
```

로그에서 `success=100`, `failed=0`을 확인한 뒤 전체 대기 청크를 처리한다.

```powershell
uv run python -m job_posting_pipeline.run_embedding
```

배치 크기를 일시적으로 바꾸려면 `--batch-size`를 사용한다.

```powershell
uv run python -m job_posting_pipeline.run_embedding --batch-size 50
```

실행 로그는 콘솔과 `logs/embedding.log`에 동시에 기록된다.

## 저장 안전장치

- 호출 제한, timeout, 연결 오류, OpenAI 5xx 오류는 지수 백오프로 재시도한다.
- 인증 실패와 잘못된 요청은 재시도하지 않고 즉시 실패한다.
- API 응답을 `index` 순서로 복원하고 응답 개수와 1536차원 여부를 검증한다.
- API 호출 뒤 `content_hash`가 그대로이고 아직 미임베딩인 행만 UPDATE한다.
- 벡터 float 정밀도를 줄이지 않고 pgvector 입력 문자열로 저장한다.
- 재시도 한도를 초과한 배치는 같은 행을 무한 호출하지 않고 작업을 중단한다.
- 실패 후 다시 실행하면 `embedding IS NULL`인 청크부터 이어서 처리한다.

## DB 확인

```sql
SELECT
    COUNT(*) FILTER (
        WHERE use_for_matching = true
          AND chunk_content <> ''
          AND embedding IS NULL
    ) AS pending,
    COUNT(*) FILTER (
        WHERE use_for_matching = true
          AND embedding IS NOT NULL
    ) AS completed
FROM job_posting_chunks
WHERE job_posting_id BETWEEN 1841 AND 2390;
```

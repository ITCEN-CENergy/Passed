# Resume pipeline 청킹 단계

현재 이 패키지는 사용자의 이력서와 자기소개서 원본을 읽어 `resume_chunks`와
`cover_letter_chunks`에 동기화하고, 별도 명령으로 PENDING 청크를 임베딩합니다.
스킬 추출은 아직 이 패키지의 범위가 아닙니다.

## 임베딩 실행

Q. 청킹 명령을 실행하면 임베딩도 함께 만들어지나요?

A. 아닙니다. 청킹과 외부 API 호출을 분리해야 원본 저장은 OpenAI 장애와 관계없이
완료되고, 실패한 임베딩만 안전하게 다시 확인할 수 있습니다. 반드시 청킹 후 임베딩
순서로 실행합니다.

```powershell
python -m resume_pipeline.run_chunking --email test@passed.dev
python -m resume_pipeline.run_embedding --email test@passed.dev --dry-run
python -m resume_pipeline.run_embedding --email test@passed.dev
```

`--dry-run`은 두 테이블의 PENDING 개수와 DB 스키마만 확인합니다. OpenAI API를
호출하거나 DB 상태를 변경하지 않으므로 `OPENAI_API_KEY` 없이도 실행할 수 있습니다.

실제 임베딩에는 `passed-ai/.env`의 다음 설정이 필요합니다.

```dotenv
OPENAI_API_KEY=...
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=1536
EMBEDDING_BATCH_SIZE=100
```

성공한 청크는 `COMPLETED`가 됩니다. DB와 Java enum이 허용하는 값이
`PENDING / PROCESSING / COMPLETED / FAILED`이므로 `DONE`은 사용하지 않습니다.

```sql
SELECT id,
       embedding_status,
       embedding_model,
       vector_dims(embedding) AS dims
FROM resume_chunks
WHERE embedding IS NOT NULL
ORDER BY id;
```

Q. 이력서를 수정한 직후 이전 임베딩이 잘못 저장될 수 있나요?

A. 작업자는 API 요청 전후의 `content_hash`가 같은 행만 저장합니다. 요청 중 원문이
바뀌면 이전 결과를 `skipped`로 버리고, 변경된 PENDING 청크를 다음 배치에서 다시
처리합니다.

Q. FAILED 청크는 일반 재실행으로 다시 처리되나요?

A. 이번 버전은 PENDING만 처리합니다. 개발 중 실패 원인을 해결한 뒤 필요한 사용자
범위의 FAILED를 명시적으로 PENDING으로 되돌려야 합니다. 전체 사용자를 무조건
초기화하지 않도록 반드시 사용자 조건을 함께 사용하세요.

## 단위 테스트 데이터와 실제 DB 데이터의 차이

Q. `tests`에 예비 이력서와 자기소개서가 있는데 CLI 결과가 왜 0건이었나요?

A. 단위 테스트의 딕셔너리와 문자열은 테스트 프로세스의 메모리에만 존재합니다.
단위 테스트는 텍스트 조립·문단 분할 같은 작은 함수의 규칙을 빠르게 확인하며,
PostgreSQL의 `users`, `resumes`, `cover_letter_items`에는 행을 저장하지 않습니다.

반면 아래 CLI는 `DATABASE_URL`이 가리키는 PostgreSQL만 조회합니다.

```powershell
python -m resume_pipeline.run_chunking --user-id 1
```

따라서 CLI를 실행하려면 해당 DB에 `resumes.user_id = 1`인 이력서와 이력서 하위
원본 데이터가 실제로 있어야 합니다. 자기소개서는 선택 입력이므로 없어도 이력서만
처리하지만, 이력서가 없으면 데이터 누락으로 간주해 즉시 실패합니다.

## 테스트 실행

일반 단위 테스트는 PostgreSQL 없이 실행됩니다.

```powershell
python -m pytest resume_pipeline/tests -m "not integration"
```

실제 PostgreSQL SQL과 청크 저장까지 검증하려면 **개발 DB와 분리된 테스트 DB**의
주소를 `TEST_DATABASE_URL`로 지정합니다. 통합 테스트가 만든 사용자·이력서·긴
자기소개서·청크는 같은 트랜잭션에서 실행되고 테스트 종료 시 롤백됩니다.

```powershell
$env:TEST_DATABASE_URL="postgresql://사용자:비밀번호@localhost:포트/테스트DB"
python -m pytest resume_pipeline/tests/test_pipeline_integration.py -m integration -v
```

Q. `DATABASE_URL`을 통합 테스트에도 재사용하면 안 되나요?

A. 오타나 테스트 실패로 개발 데이터에 영향을 줄 위험을 줄이기 위해 변수 이름부터
분리했습니다. 테스트 전용 DB를 명시해야만 통합 테스트가 실행됩니다.

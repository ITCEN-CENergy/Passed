# Resume pipeline 청킹 단계

현재 이 패키지는 사용자의 이력서와 자기소개서 원본을 읽어 `resume_chunks`와
`cover_letter_chunks`에 동기화합니다. 임베딩과 스킬 추출은 아직 이 명령의 범위가
아닙니다.

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

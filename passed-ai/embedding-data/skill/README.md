# 스킬 임베딩 생성 및 적재

`skills.name`과 `skills.description`만 아래 형식으로 조합해
`text-embedding-3-small`의 1,536차원 벡터를 생성하고 `skills.id` 기준으로
기존 행의 `embedding`만 업데이트합니다.

```text
스킬명: {name}
설명: {description}
```

## 환경 변수

`passed-ai/.env`에 다음 값을 설정합니다.

```dotenv
DATABASE_URL=postgresql://edu:1234@localhost:5433/edu
OPENAI_API_KEY=...
SKILL_EMBEDDING_MODEL=text-embedding-3-small
SKILL_EMBEDDING_DIMENSION=1536
SKILL_EMBEDDING_BATCH_SIZE=100
SKILL_EMBEDDING_MAX_RETRIES=5
SKILL_EXPECTED_COUNT=1654
```

## 실행

```bash
cd passed-ai/embedding-data/skill
uv run python -m skill_pipeline.cli embed
```

기본 실행은 `embedding IS NULL`인 행만 처리하므로 재실행해도 행이 추가되거나
중복 처리되지 않습니다. 원문 변경 등으로 전체 벡터를 재생성할 때만
`embed --force`를 사용합니다. API의 rate limit, timeout, 연결 오류 및 5xx 오류는
지수 백오프로 최대 `SKILL_EMBEDDING_MAX_RETRIES`회 재시도합니다.

완료 시 `missing=0 wrong_dimension=0`이어야 합니다. 작업 전후의
`id/category/name/description/created_at` 스냅샷도 비교하므로 기존 데이터가
바뀌면 실패합니다.

## 유사 스킬 검색

```bash
uv run python -m skill_pipeline.cli search "대규모 트래픽을 처리하는 백엔드 개발" --limit 10
uv run python -m skill_pipeline.cli search "클라우드 보안" --category TECHNICAL_SKILL
```

검색은 pgvector의 cosine distance를 사용하고 `category` 필터를 선택적으로
적용합니다.

## 테스트

```bash
uv run pytest embedding-data/skill/tests -q
```

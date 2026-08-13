# Resume pipeline 청킹 단계

현재 이 패키지는 사용자의 이력서와 자기소개서 원본을 읽어 `resume_chunks`와
`cover_letter_chunks`에 동기화하고, 별도 명령으로 PENDING 청크를 임베딩합니다.
임베딩이 끝나면 별도 명령으로 마스터 매핑 전 스킬 후보를 JSON으로 추출합니다.

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

## 스킬 후보 추출

Q. 후보 추출 결과가 바로 `user_skills`에 저장되나요?

A. 아닙니다. 지금 단계는 프롬프트 품질을 검증하기 위한 매핑 전 후보만 생성합니다.
결과에는 청크 ID, `content_hash`, 카테고리, 원문 근거가 포함되며 DB는 변경하지
않습니다.

먼저 대상 청크를 확인합니다.

```powershell
python -m resume_pipeline.run_skill_extraction `
  --email test@passed.dev `
  --dry-run
```

실제 후보를 JSON 파일로 만들려면 다음 설정과 명령을 사용합니다.

```dotenv
OPENAI_API_KEY=...
SKILL_EXTRACTION_MODEL=gpt-4o-mini
SKILL_EXTRACTION_MAX_RETRIES=3
SKILL_EXTRACTION_TIMEOUT_SECONDS=60
```

```powershell
python -m resume_pipeline.run_skill_extraction `
  --email test@passed.dev `
  --output skill_candidates_test_user.json
```

이력서는 `source_type`, 자기소개서는 `question_type`을 프롬프트에 함께 전달합니다.
상세 규칙은 `SKILL_EXTRACTION_PROMPT_SPEC.md`에 있습니다.

## 골든셋 F1 평가

초기 골든셋은 `resume_pipeline/evaluation/golden_skill_extraction.json`에 있습니다.
이는 팀 검토 전 초안이며 실제 문서 사례로 계속 확장해야 합니다.

```powershell
python -m resume_pipeline.run_skill_eval `
  --golden resume_pipeline/evaluation/golden_skill_extraction.json `
  --predictions predictions.json
```

실제 모델로 골든셋 예측 생성과 평가를 함께 실행할 수도 있습니다.

```powershell
python -m resume_pipeline.run_skill_eval `
  --golden resume_pipeline/evaluation/golden_skill_extraction.json `
  --generate `
  --save-predictions predictions.json
```

`--generate`는 골든셋 예제마다 OpenAI API를 호출하므로 비용이 발생합니다.

평가는 이름과 카테고리의 정확 일치 기준으로 micro Precision/Recall/F1과
카테고리별 점수를 출력합니다. 스킬 마스터 동의어·유사도 매핑 평가는 다음 단계에서
별도로 수행합니다.

## deterministic recovery 제거 실험

Q. `_EXPLICIT_COMPLETED_SKILL_RULES`를 왜 바로 삭제하지 않나요?

A. 현재 규칙이 고정해 주던 후보를 Pass 2가 실제로 복구하는지 확인하지 않고 삭제하면
Recall이 조용히 낮아질 수 있습니다. 운영 기본값은 유지하고 평가에서만 전체 또는 특정
규칙을 끈 뒤 같은 입력으로 비교합니다.

```powershell
python -m resume_pipeline.run_skill_extraction `
  --user-id 56 `
  --disable-all-recovery-rules `
  --output recovery_off.json

python -m resume_pipeline.run_skill_eval `
  --golden resume_pipeline/evaluation/golden_skill_extraction.json `
  --generate `
  --disable-recovery-rule "콘텐츠 생성" `
  --save-predictions predictions_without_content_generation.json `
  --save-report metrics_without_content_generation.json
```

평가 보고서에는 프롬프트뿐 아니라 활성 recovery 규칙까지 포함한
`pipeline_sha256`이 기록됩니다.

## 반복 실행 안정성

같은 입력을 세 번 실행한 결과는 청크별·전체 후보 집합 Jaccard로 비교합니다.

```powershell
python -m resume_pipeline.run_skill_stability_eval `
  --extractions extraction_run1.json `
  --extractions extraction_run2.json `
  --extractions extraction_run3.json `
  --output extraction_stability.json
```

두 실행이 모두 빈 후보인 음성 예제는 결과가 동일하므로 Jaccard 1로 계산합니다.

## 정규화 감사

현재 정규화는 공백·점·밑줄·하이픈을 제거합니다. 이를 바로 바꾸지 않고 NFKC,
casefold, trim, 연속 공백 축소만 수행하는 보수적 전략과 먼저 비교합니다.

```powershell
python -m resume_pipeline.run_skill_normalization_audit `
  --golden resume_pipeline/evaluation/golden_skill_mapping.json `
  --output normalization_audit.json
```

`stored_alias_mismatch_conservative`가 0이 아니면 Python 함수만 바꾸면 안 됩니다.
`skill_aliases.normalized_alias`도 새 Flyway 마이그레이션으로 함께 이관해야 합니다.

## Master Top-K Retrieval과 Pass 2 preview

Q. Retrieval이 스킬을 바로 확정하나요?

A. 아닙니다. 기존 Pass 1에서 이미 매핑된 `skill_id`를 제외한 뒤, 저장된 청크
embedding과 `skills.embedding`으로 TECHNICAL_SKILL·BEHAVIORAL_TRAIT 후보만 찾습니다.
Pass 2는 제시된 ID 중 원문에서 직접 입증된 후보만 고릅니다. 이 명령은 DB를 수정하지
않습니다.

```powershell
python -m resume_pipeline.run_skill_recall_experiment `
  --user-id 56 `
  --extraction-input recovery_off.json `
  --mapping-input recovery_off_mapping.json `
  --top-k 20 `
  --top-k 40 `
  --verify-top-k 20 `
  --output recovery_pass2_preview.json
```

`--mapping-input`을 생략하면 현재 Pass 1 매핑기를 읽기 전용으로 실행합니다. 기존
preview를 넣으면 후보명 임베딩 API를 다시 호출하지 않습니다. `--verify-top-k`를
지정한 경우에만 Pass 2 LLM 비용이 발생합니다.

### 청크/문장/Hybrid Retrieval A/B/C 비교

DB 청크는 그대로 유지하면서 Retrieval 때만 임시 문장으로 나누어 비교할 수 있습니다.
`chunk`가 기본값이므로 기존 동작은 바뀌지 않습니다. `sentence`는 문장 임베딩 API를
호출하지만 DB에는 저장하지 않으며, 같은 실행의 Top 20/40 비교에서는 벡터를
재사용합니다. `hybrid`는 chunk와 sentence 후보의 합집합에서 같은 `skill_id`의
최고 유사도만 남기고 카테고리별 최종 K개를 선택합니다.

```powershell
python -m resume_pipeline.run_skill_recall_experiment `
  --user-id 56 `
  --extraction-input resume_pipeline/evaluation/baselines/20260812_user56_extraction_recovery_off.json `
  --mapping-input resume_pipeline/evaluation/baselines/20260812_user56_mapping_recovery_off.json `
  --retrieval-mode chunk `
  --retrieval-mode sentence `
  --retrieval-mode hybrid `
  --sentence-top-k 5 `
  --top-k 20 `
  --top-k 40 `
  --output resume_pipeline/evaluation/baselines/retrieval_chunk_sentence_ab.json
```

후보에는 `retrieval_source`가 기록됩니다. 문장 검색이 대표 결과라면 가장 높은
유사도를 만든 `matched_sentence`/`sentence_index`도 함께 남깁니다. 그 뒤
카테고리별 최종 K개만 Pass 2 후보로 사용합니다. 여러 모드를 동시에 비교할 때는
Pass 2를 실행하지 않습니다.

### Strict Pass 2 preview

`--strict-pass2`는 Hybrid 후보를 의미적으로 관련 있는지 판단하는 대신, 원문이 사용자
보유를 직접 증명하는지 Precision 우선으로 검증합니다. TECHNICAL_SKILL은 canonical
name 또는 활성 alias의 직접 명시가 필요하고, BEHAVIORAL_TRAIT은 완료 행동이 직접
입증되어야 합니다. 행동 특성은 canonical/alias 직접 명시 또는 마스터 설명의 구별력
있는 근거 앵커가 필요하며, 단순 개선·공유·문서화만으로 성향을 승격하지 않습니다.
결과는 `NEW_SKILL_RECOVERY`와 `EVIDENCE_ENRICHMENT`로 구분하며,
Pass 1과 동일한 `skill_id + source + chunk + evidence`는 제외합니다.

```powershell
python -m resume_pipeline.run_skill_recall_experiment `
  --user-id 56 `
  --extraction-input recovery_off.json `
  --mapping-input recovery_off_mapping.json `
  --retrieval-mode hybrid `
  --sentence-top-k 5 `
  --top-k 40 `
  --verify-top-k 40 `
  --strict-pass2 `
  --output strict_pass2_preview.json
```

이 명령은 preview 전용이며 `user_skills`나 `user_skill_evidences`를 저장하지 않습니다.
평가는 `run_pass2_eval`로 수행하며 REVIEW 라벨은 주 지표에서 제외합니다.

## Unmapped 후보 JSON 집계

운영 검수 흐름이 확정되기 전에는 새 테이블을 만들지 않고 mapping preview를
집계합니다.

```powershell
python -m resume_pipeline.run_unmapped_skill_report `
  --mapping skill_mapping_run1.json `
  --mapping skill_mapping_run2.json `
  --output unmapped_summary.json
```

보수적으로 정규화한 이름과 카테고리별로 반복 횟수, 표본 이름·근거, 실패 사유를
저장합니다. 관리자 승인·거절 상태를 실제로 운영할 때만 별도 DB 테이블을 추가합니다.

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

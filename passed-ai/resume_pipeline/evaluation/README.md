# 스킬 후보 추출 골든셋

`golden_skill_extraction.json`은 프롬프트의 첫 회귀 테스트를 위한 **34개 초안**입니다.
EXPERIENCE와 BEHAVIORAL_TRAIT를 중심으로 구성했고, 미래 포부·근거 없는 자기평가처럼
정답이 빈 예제도 포함했습니다. 자격증 취득 전 상태, 기술명 표기 흔들림, 한 문장의
복수 클라우드 서비스도 별도로 검증합니다. 팀이 실제 사례를 검토해 정답 이름,
카테고리, level을 확정해야 합니다.

## 추출 정답과 매핑 정답을 분리하는 이유

`golden_skill_extraction.json`은 원문에서 관찰할 수 있는 구체 후보만 평가합니다.
예를 들어 `팀원 상황 청취`를 올바른 BEHAVIORAL 후보로 보고, 이것을 마스터의 `공감`으로
연결하는지는 별도 `golden_skill_mapping.json`에서 평가합니다.

Q. 왜 처음부터 `공감`을 추출 정답으로 두지 않나요?

A. 원문 후보 추출과 마스터 의미 연결을 한 점수에 섞으면 `팀원 상황 청취`를 잘 찾고도
문자열이 다르다는 이유로 추출 실패가 됩니다. 두 단계를 나누면 프롬프트 문제인지,
EXACT/KEYWORD/EMBEDDING 매핑 문제인지 구분할 수 있습니다.

매핑 골든셋은 환경마다 달라질 수 있는 숫자 ID 대신 마스터의 unique 이름과 카테고리를
정답으로 사용합니다. `should_map=false`는 현재 마스터에 적절한 항목이 없으므로 자동으로
가까운 상위 스킬에 붙이면 안 되는 사례입니다.

```json
{
  "case_id": "map-teammate-listening",
  "extracted_name": "팀원 상황 청취",
  "extracted_category": "BEHAVIORAL_TRAIT",
  "should_map": true,
  "expected_skill_name": "공감",
  "expected_skill_category": "BEHAVIORAL_TRAIT",
  "allowed_mapping_methods": ["EMBEDDING"],
  "rationale": "상대 상황을 먼저 듣고 고려"
}
```

현재 매핑 골든셋은 47건이며 41건은 매핑 성공, 6건은 미매핑이 정답입니다. 실제
`map_to_skill` 구현 후에는 top-1 정확도뿐 아니라 잘못 자동 매핑한 비율과 미매핑률을
별도로 측정해야 합니다.

Q. 왜 skills 마스터 ID를 정답에 넣지 않나요?

A. 이 평가는 문장에서 후보를 찾는 능력만 측정합니다. 마스터 ID까지 넣으면 후보 추출
오류와 EXACT/KEYWORD/EMBEDDING 매핑 오류가 섞여 원인을 구분하기 어렵습니다.

예측 파일은 다음 형식입니다.

```json
[
  {
    "example_id": "resume-certification-001",
    "predicted": [
      {"extracted_name": "정보처리기사", "category": "CERTIFICATION", "level": 1}
    ]
  }
]
```

평가 명령:

```powershell
python -m resume_pipeline.run_skill_eval `
  --golden resume_pipeline/evaluation/golden_skill_extraction.json `
  --predictions predictions.json
```

골든셋을 실제 OpenAI 추출기로 실행하고 즉시 평가하려면 다음 명령을 사용한다.
이 명령은 예제 수만큼 API를 호출하므로 실행 전 비용과 모델 설정을 확인한다.

```powershell
python -m resume_pipeline.run_skill_eval `
  --golden resume_pipeline/evaluation/golden_skill_extraction.json `
  --generate `
  --save-predictions resume_pipeline/evaluation/baselines/20260804_gpt-4o-mini_predictions.json `
  --save-report resume_pipeline/evaluation/baselines/20260804_gpt-4o-mini_metrics.json
```

`evidence`는 반드시 원문에 있는 연속된 표현이어야 하고, `extracted_name`은 그 근거가
입증하는 역량 하나를 짧게 원자화한 이름입니다. 평가는 이름을 비교할 때 대소문자와
연속 공백만 정규화합니다. 동의어를 같은 정답으로 인정하는 일은 다음 마스터 매핑
평가에서 다룹니다.

평가 결과는 후보 추출 Precision/Recall/F1, 올바르게 추출된 후보의 level 정확도·MAE,
빈 정답 예제의 과잉 추출률을 분리해 보여줍니다.

현재 골든셋은 초안입니다. 실제 API 베이스라인을 실행할 때 모델명, 프롬프트 버전,
실행일과 함께 예측 JSON 및 평가 JSON을 `evaluation/baselines/` 아래에 보관합니다.

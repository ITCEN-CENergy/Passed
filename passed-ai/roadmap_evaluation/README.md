# 학습자료 추천 품질 기준선 평가

외부 검색 API나 OpenAI API를 호출하지 않고, DB에 이미 저장된 추천 결과만 평가합니다.

## 표본 추출

최근 활성/완료 로드맵에서 역량 10개, 역량별 최대 3개를 추출합니다. 같은 역량의 동일 URL은 한 번만 포함합니다.

```bash
.venv/bin/python -m roadmap_evaluation.resource_quality_eval export \
  --database-url postgresql://edu:1234@localhost:5433/edu \
  --output roadmap_evaluation/baseline.csv
```

특정 로드맵은 `--roadmap-id 1704`처럼 지정합니다.

## 수동 라벨링

| 열 | 값 | 기준 |
|---|---|---|
| `competency_relevance` | 0/1/2 | 무관 / 부분 관련 / 직접 관련 |
| `milestone_relevance` | 0/1/2 | 목표와 무관 / 보조 가능 / 목표 수행에 직접 사용 가능 |
| `difficulty_fit` | 0/1/2 | 부적합 / 일부 적합 / 적합 |
| `accessible` | yes/no | 링크에서 자료를 실제 이용할 수 있는지 |
| `current` | yes/no/unknown | 폐기되거나 명백히 낡은 내용인지 |
| `language` | ko/en/mixed/other | 자료의 주 언어 |
| `duplicate` | yes/no | 표본 안에서 내용상 같은 자료인지 |
| `notes` | 자유 입력 | 무관한 분야, 링크 오류 등 실패 원인 |

적합 추천은 `역량 관련성 >= 1`, `마일스톤 관련성 >= 1`, `접근 가능=yes`를 모두 만족한 자료입니다.

## 기준선 계산

```bash
.venv/bin/python -m roadmap_evaluation.resource_quality_eval summarize \
  --input roadmap_evaluation/baseline.csv \
  --output roadmap_evaluation/baseline-report.json
```

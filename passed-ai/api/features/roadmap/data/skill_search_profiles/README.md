# Skill search profiles

로드맵 학습자료 검색에 사용하는 검수된 스킬별 검색 프로필입니다.

- 파일은 역량 카테고리별 JSON 객체입니다.
- 객체 키는 `standardCompetencyId`입니다.
- `queries.ko`, `queries.en`은 검색에 함께 사용하는 구문입니다.
- `excludeTerms`는 검색 결과의 제목 또는 설명에 포함되면 제외할 구문입니다.
- `reviewed=true`는 사람이 검색 의도를 검수했다는 뜻입니다.
- 등록되지 않은 스킬은 DB 이름·설명·활성 별칭으로 자동 검색합니다.

현재 DB의 전체 스킬 1,655개가 포함되어 있습니다. `reviewed=false` 프로필은
전체 스킬 말뭉치에서 희소한 핵심어를 계산해 자동 생성한 초깃값이고,
`reviewed=true` 프로필은 실제 추천 결과를 보고 사람이 검수한 항목입니다.

DB 스킬이 변경되면 다음 명령으로 전체 JSON을 다시 생성합니다. 기존의
`reviewed=true` 항목은 보존됩니다.

```bash
.venv/bin/python -m roadmap_evaluation.generate_skill_search_profiles \
  --database-url "$DATABASE_URL" \
  --output-directory api/features/roadmap/data/skill_search_profiles
```

동일한 스킬 ID를 둘 이상의 파일에 등록하면 애플리케이션이 오류로 처리합니다.

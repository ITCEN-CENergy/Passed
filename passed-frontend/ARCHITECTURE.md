# 프론트엔드 구조

```text
src/
├── app/                 # 라우터, 최상위 레이아웃, Provider, 전역 Store
├── shared/              # 특정 도메인에 속하지 않는 재사용 코드
│   ├── api/
│   ├── components/
│   ├── hooks/
│   └── utils/
└── features/            # 사용자 기능 단위 모듈
    ├── auth/
    ├── user/
    ├── resume/
    ├── skill/
    ├── job-posting/
    ├── cover-letter/
    ├── analysis/
    └── roadmap/
```

각 feature는 필요할 때 `api`, `components`, `hooks`를 사용합니다. 현재 프로젝트는
JavaScript 기반이므로 TypeScript 전환 전까지 별도의 `types` 디렉터리는 두지 않습니다.

독립 URL을 갖는 `auth`, `user`, `resume`, `job-posting`, `cover-letter`, `analysis`,
`roadmap`에는 라우트 단위 화면을 배치하는 `pages` 디렉터리를 둡니다. `skill`은 현재
다른 기능 화면에서 사용하는 도메인으로 보고 독립 `pages`를 두지 않습니다.

의존성 방향은 `app → features → shared`입니다. `shared`는 `features`를 참조하지 않고,
한 feature에서만 쓰는 코드를 성급하게 `shared`로 옮기지 않습니다.

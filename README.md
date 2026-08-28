# [Passed]

> "취업 준비부터 역량 성장까지 함께하는 AI 커리어 코칭 플랫폼"

<table>
  <tr>
    <td width="50%"><img width="100%" alt="Passed 프로젝트 대표 이미지 1" src="./passed-frontend/public/result_img/randing1.png" /></td>
    <td width="50%"><img width="100%" alt="Passed 프로젝트 대표 이미지 2" src="./passed-frontend/public/result_img/randing2.png" /></td>
  </tr>
</table>

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [프로젝트 기획 배경](#2-프로젝트-기획-배경)
3. [구성원 및 역할](#3-구성원-및-역할)
4. [기술 스택](#4-기술-스택)
5. [주요 기능](#5-주요-기능)
6. [실행 방법](#6-실행-방법)
7. [프로젝트 아키텍처](#7-프로젝트-아키텍처)
8. [협업 컨벤션](#8-협업-컨벤션)
9. [트러블 슈팅](#9-트러블-슈팅)
10. [시연 화면](#10-시연-화면)

---

## 1. 프로젝트 개요

- **프로젝트명**: `Passed`
- **한 줄 설명**: `취업 준비부터 역량 성장까지 함께하는 AI 커리어 코칭 플랫폼`
- **진행 기간**: `2026.07.29 ~ 2026.08.18`
- **개발 인원**: `4명`
- **팀명**: `CENergy`
- **GitHub Repository**: [`GitHub`](https://github.com/ITCEN-CENergy/Passed)
- **배포 주소**: [`Passed 바로가기`](https://passed-career.store/)

---

## 2. 프로젝트 기획 배경

### 시장·사회적 현황

- **방대한 채용 정보와 탐색 부담**: 공공기관과 민간 채용 플랫폼을 통해 수많은 채용 공고가 제공되고 있지만, 구직자가 자신의 경력과 역량에 적합한 공고를 직접 비교하고 선별하는 데 많은 시간과 노력이 필요합니다.
- **직무 중심 채용과 역량 개발의 중요성 확대**: 단순한 공고 탐색을 넘어 지원 직무와 현재 역량의 적합도를 객관적으로 파악하고, 부족한 역량을 체계적으로 보완할 수 있는 취업 준비 과정이 중요해지고 있습니다.
- **개인 맞춤형 취업 지원 수요 증가**: 대학·교육기관과 취업지원 서비스에서도 구직자의 준비 상태를 데이터로 진단하고, 개인별 공고 추천과 학습 방향을 제시할 수 있는 도구가 요구되고 있습니다.

### 기존 서비스의 한계

- **조건 중심의 채용공고 검색**: 키워드와 필터에 의존하는 검색 방식만으로는 이력서에 나타난 경험과 보유 역량을 종합적으로 반영하기 어렵고, 구직자가 자신에게 적합한 공고를 판단해야 하는 부담이 남습니다.
- **객관적인 준비도 진단 부족**: 지원하려는 공고에서 요구하는 역량과 자신의 현재 역량 사이의 차이를 구체적으로 확인하기 어려워, 어떤 부분을 우선 보완해야 하는지 결정하기 쉽지 않습니다.
- **추천 이후 준비 과정의 단절**: 채용공고 추천, 자기소개서 작성, 부족 역량 학습이 서로 분리되어 있어 공고 탐색부터 실제 지원까지 일관된 취업 준비 방향을 제공하기 어렵습니다.

### 해결 방안

- **AI 기반 맞춤형 채용공고 추천**: 구직자의 이력서와 보유 역량을 채용공고의 직무 요건과 의미 기반으로 비교하여 적합한 공고를 추천하고, 방대한 공고를 탐색하는 시간을 줄입니다.
- **역량 진단 리포트 제공**: 추천 공고가 요구하는 역량과 구직자의 현재 역량을 비교하여 강점, 부족한 역량, 준비 적합도를 객관적인 결과로 제공합니다.
- **개인 맞춤형 학습 로드맵 생성**: 분석된 역량 차이를 바탕으로 필요한 학습 내용과 자료를 제안하여 구직자가 체계적이고 자기주도적으로 역량을 개발할 수 있도록 지원합니다.
- **채용공고 기반 자기소개서 첨삭**: 지원하려는 공고의 요구사항을 반영해 자기소개서를 분석하고 피드백과 수정 방향을 제공함으로써 실제 지원 과정까지 연결합니다.
- **기대 효과**: 구직자는 자신의 준비 상태와 보완점을 명확히 파악하고 일관된 방향으로 취업을 준비할 수 있으며, 직무 적합도와 취업 경쟁력을 높일 수 있습니다.

---

## 3. 구성원 및 역할

### 구성원

| <img width="120" height="160" alt="강민주" src="https://github.com/user-attachments/assets/86ecb961-bf83-4c18-bdd7-5624699b18c4" /> | <img width="120" height="160" alt="신재욱" src="https://github.com/user-attachments/assets/9b47c974-ce65-422a-b756-4039843bb377" /> | <img width="120" height="160" alt="이민호" src="https://github.com/user-attachments/assets/eb3fb149-a4d6-430c-933d-391c936da4cf" /> | <img width="120" height="160" alt="조윤지" src="https://github.com/user-attachments/assets/6e53a579-c869-4bb7-9c9d-6099d1454a44" /> |
| :---: | :---: | :---: | :---: |
| **강민주** | **신재욱** | **이민호** | **조윤지** |
| [GitHub](https://github.com/mimo626) | [GitHub](https://github.com/tls427wodnr) | [GitHub](https://github.com/minho2618) | [GitHub](https://github.com/YUMZII) |

### 역할 분담

| 이름  | 역할 | 담당 업무 |
|-----| -- | --- |
| 강민주 | 팀장 | • **채용공고 추천 도메인 설계 및 백엔드 API 구현**<br>• **사용자·채용공고 스킬 매칭과 추천 점수·등급 산정 로직 구현**<br>• **LLM 기반 미충족 스킬 검증 및 채용공고 추천 설명 생성 구현**<br>• **단일·다건 채용공고 추천 실행과 결과 저장·조회 기능 구현**<br>• **채용공고 조회·추천 결과·적합도 분석 프론트엔드 UI 구현**|
| 신재욱 | 팀원 | • **학습 로드맵 도메인 설계 및 백엔드 API 구현**<br>• **LLM 기반 맞춤형 로드맵 생성·재계획 및 학습 자료 추천 구현**<br>• **로드맵 생성 성능 최적화와 동시성·멱등성 안정화**<br>• **학습 로드맵 프론트엔드 UI 및 Zustand 상태 관리 구현** |
| 이민호 | 팀원 | • **프로젝트 운영 인프라 설계 및 구축**<br>• **사용자 인증 및 인가 구현**<br>• **LLM 기반 자기소개서 첨삭 설계 및 구현** |
| 조윤지 | 팀원 | • **이력서·공통 자기소개서 도메인**<br>• **이력서·자기소개서 청킹 및 pgvector 임베딩 파이프라인 구현**<br>• **LLM 기반 스킬 후보 추출·표준 스킬 매핑·근거 저장 파이프라인 설계 및 구현**<br>• **골든셋 기반 단계별 성능 평가와 Hybrid Retrieval·Strict Pass 검증 고도화** |
 |

---

## 4. 기술 스택

| 구분 | 기술 스택                                                                                                            |
| --- |------------------------------------------------------------------------------------------------------------------|
| **Backend** | Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, Hibernate, QueryDSL, Spring Security, JWT, Flyway, Gradle |
| **Frontend** | JavaScript, React 19, React Router 7, Zustand 5, Vite 8, ESLint                                                  |
| **AI** | Python 3.14, FastAPI, LangChain, OpenAI API, FastMCP, Pydantic, NumPy                                            |
| **Database / Data** | PostgreSQL 17, pgvector, Psycopg, CSV 기반 채용공고 데이터 파이프라인                                                          |
| **Infrastructure / CI/CD** | Docker, Docker Compose, Nginx, Oracle Cloud Infrastructure, GitHub Actions, GitHub Container Registry            |
| **Testing** | JUnit 5, Spring Boot Test, Spring Security Test, Pytest                                                          |
| **Monitoring** | Spring Boot Actuator, Micrometer, Prometheus, PostgreSQL Exporter, Grafana                                       |
| **Collaboration** | Notion, Jira, Git, GitHub, Flyway                                                                                |

---

## 5. 주요 기능



<details>
<summary><strong>이력서·자기소개서 기반 AI 보유 역량 분석</strong></summary>

<br>

- **기능 설명**: 사용자가 작성한 이력서와 공통 자기소개서를 분석하여 기술, 경험, 행동 특성, 자격증을 표준 스킬로 구조화하고, 각 스킬을 판단한 원문 근거와 숙련도를 함께 제공합니다. 분석 결과에서 중요 스킬을 직접 선택·수정할 수 있으며 이후 채용공고 추천과 역량 진단의 사용자 스킬 데이터로 활용됩니다.
- **구현 내용**: 이력서의 경력·교육·활동 등 항목과 자기소개서 문항을 의미 단위로 청킹하고 pgvector 임베딩을 생성했습니다. Pass 1에서 LLM이 원문으로부터 스킬명·카테고리·레벨·근거를 구조화하여 추출한 뒤 `EXACT → NORMALIZED → ALIAS → EMBEDDING` 순서로 표준 스킬 사전에 매핑합니다. 매핑 결과는 Strict Pass 1로 재검증하고, 청크·문장 임베딩을 결합한 Hybrid Retrieval로 누락 가능성이 있는 마스터 후보를 검색한 뒤 Strict Pass 2를 통과한 스킬만 복구합니다. 최종 결과는 `skill_id` 기준으로 병합하여 사용자 스킬과 복수의 원문 근거를 트랜잭션으로 저장하며, 문서·파이프라인 해시를 이용해 변경이 없는 분석의 중복 실행을 방지했습니다.
- **사용 기술**: Python, FastAPI, OpenAI API, Pydantic, PostgreSQL, pgvector, Psycopg, Java 21, Spring Boot, Spring Data JPA, React
- **사용자에게 제공하는 가치**: 사용자는 이력서에 직접 적은 기술뿐 아니라 프로젝트와 업무 경험에 드러난 역량까지 확인할 수 있습니다. 각 스킬의 출처와 근거 문장을 함께 제공하여 AI 분석 결과를 검토할 수 있고, 표준화된 스킬 데이터를 기반으로 자신에게 적합한 채용공고와 부족 역량을 더 정확하게 추천받을 수 있습니다.
- **기능 설명**: 사용자가 작성한 이력서와 공통 자기소개서를 분석하여 기술, 경험, 행동 특성, 자격증을 표준 스킬로 구조화하고, 각 스킬을 판단한 원문 근거와 숙련도를 함께 제공합니다. 분석 결과에서 중요 스킬을 직접 선택·수정할 수 있으며 이후 채용공고 추천과 역량 진단의 사용자 스킬 데이터로 활용됩니다.
- **구현 내용**: 이력서의 경력·교육·활동 등 항목과 자기소개서 문항을 의미 단위로 청킹하고 pgvector 임베딩을 생성했습니다. 검증1에서 LLM이 원문으로부터 스킬명·카테고리·레벨·근거를 구조화하여 추출한 뒤 `EXACT → NORMALIZED → ALIAS → EMBEDDING` 순서로 표준 스킬 사전에 매핑합니다. 매핑 결과는 Strict Pass 1로 재검증하고, 청크·문장 임베딩을 결합한 Hybrid Retrieval로 누락 가능성이 있는 마스터 후보를 검색한 뒤 검증2를 통과한 스킬만 복구합니다. 최종 결과는 `skill_id` 기준으로 병합하여 사용자 스킬과 복수의 원문 근거를 트랜잭션으로 저장하며, 문서·파이프라인 해시를 이용해 변경이 없는 분석의 중복 실행을 방지했습니다.
- **사용 기술**: Python, FastAPI, OpenAI API, Pydantic, PostgreSQL, pgvector, Psycopg, Java 21, Spring Boot, Spring Data JPA, React
- **사용자에게 제공하는 가치**: 사용자는 이력서에 직접 적은 기술뿐 아니라 프로젝트와 업무 경험에 드러난 역량까지 확인할 수 있습니다. 각 스킬의 출처와 근거 문장을 함께 제공하여 AI 분석 결과를 검토할 수 있고, 표준화된 스킬 데이터를 기반으로 자신에게 적합한 채용공고와 부족 역량을 더 정확하게 추천받을 수 있습니다.

<br>

</details>

<details>
<summary><strong>AI 기반 맞춤형 채용공고 추천 및 적합도 분석</strong></summary>

<br>

- **기능 설명**: 사용자의 이력서와 자기소개서에서 추출한 보유 역량을 채용공고의 자격요건·우대사항·관련 스킬과 비교하여 적합한 채용공고를 추천합니다. 각 채용공고의 필수 스킬 보유율과 숙련도, 중요 스킬 일치 여부를 분석하여 추천 점수와 등급을 산정하고, 사용자의 강점과 보완할 역량 및 추천 이유를 함께 제공합니다.
- **구현 내용**: 사용자가 선택한 산업과 직무를 기준으로 추천 후보 채용공고를 조회하고, 사용자 스킬과 채용공고 스킬을 ID 기준으로 우선 매칭했습니다. 필수·우대·관련 스킬의 중요도와 사용자 숙련도를 반영하여 점수를 계산하고, 필수 스킬 보유율과 숙련도 기준을 통과한 채용공고 중 상위 12개를 선별했습니다. ID 매칭에서 누락된 미충족 스킬은 이력서·자기소개서 원문을 대상으로 임베딩 검색과 LLM 교차 검증을 수행했으며, 구체적인 수행 행동이 확인된 경우에만 해당 추천 실행의 임시 충족 스킬로 반영했습니다. 강점·보완점·추천 점수·등급은 애플리케이션의 비즈니스 로직으로 산정하고, 검증된 결과를 기반으로 한 추천 이유와 성장 방향만 LLM이 생성하도록 역할을 분리했습니다.
- **사용 기술**: Java 21, Spring Boot, Spring Data JPA, QueryDSL, PostgreSQL, pgvector, Python, FastAPI, Pydantic, OpenAI API, React, React Query
- **사용자에게 제공하는 가치**: 사용자는 자신의 산업과 직무에 적합한 채용공고를 우선순위에 따라 확인하고, 채용공고별 적합도와 지원 가능성을 구체적으로 파악할 수 있습니다. 단순한 점수뿐만 아니라 충족한 역량, 부족한 역량, 추천 이유와 성장 방향을 함께 제공하여 지원할 채용공고를 합리적으로 선택하고 부족한 취업 역량을 보완할 수 있습니다.

<br>

</details>

<details>
<summary><strong>AI 기반 개인 맞춤형 학습 로드맵</strong></summary>

<br>

- **기능 설명**: 사용자가 선택한 여러 채용공고와 현재 보유 역량을 비교하여 부족한 역량을 통합하고, 우선순위에 따른 개인 맞춤형 학습 로드맵을 생성합니다. 학습 진행률과 예상 완료일을 추적하며 일정이 지연되면 AI 기반 재계획도 지원합니다.
- **구현 내용**: 공고별 Skill Gap을 표준 역량 기준으로 병합하고 중요도와 부족 수준에 따라 학습 대상을 선별했습니다. LLM으로 단계별 마일스톤을 생성하고 MCP 기반 외부 학습자료를 검색한 뒤, 애플리케이션의 이중 검증을 통과한 결과만 저장하도록 구성했습니다. 비동기 병렬 처리와 전체 타임아웃으로 생성 시간을 최적화했으며, 생성 키와 DB 유일 제약으로 중복 생성을 방지하고 비관적 잠금과 재계획 토큰으로 동시 요청의 멱등성을 보장했습니다.
- **사용 기술**: Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Python, FastAPI, Pydantic, OpenAI API, FastMCP, HTTPX, React, Zustand
- **사용자에게 제공하는 가치**: 사용자는 여러 목표 공고에서 공통으로 요구되는 역량과 자신의 부족한 부분을 한눈에 확인하고, 검증된 학습자료와 단계별 계획을 따라 체계적으로 취업 역량을 강화할 수 있습니다. 실제 진도에 맞춰 완료 예상일과 계획이 자동으로 조정되어 지속 가능한 학습을 돕습니다.

<br>

</details>

<details>
<summary><strong>자기소개서 피드백 및 첨삭</strong></summary>

<br>

- **기능 설명**: 사용자가 작성한 자기소개서를 채용 공고의 직무 내용과 요구 역량에 맞춰 AI가 분석하는 기능입니다. 문항별 피드백과 자기소개서 전체 평가를 제공하며, 강점·개선점·수정 예시를 통해 첨삭 방향을 구체적으로 안내합니다.
- **구현 내용**: 자기소개서 문항, 작성 내용, 채용 공고, 사용자 보유 기술을 AI에 전달하여 문항별 평가와 첨삭안을 생성하도록 구현했습니다. 전체 자기소개서에 대해서는 종합 점수, 요약 진단, 강점, 개선 사항을 제공하며, 생성된 결과는 데이터베이스에 저장해 다시 조회할 수 있습니다. 원문이나 채용 공고가 변경되면 기존 피드백이 잘못 노출되지 않도록 처리했습니다.
- **사용 기술**: React, Spring Boot, FastAPI, PostgreSQL, OpenAI API, Pydantic
- **사용자에게 제공하는 가치**: 사용자는 단순한 문장 교정을 넘어 지원 기업과 직무에 적합한 자기소개서인지 객관적으로 확인할 수 있습니다. 구체적인 강점과 보완점, 수정 예시를 함께 제공받아 첨삭에 드는 시간과 부담을 줄이고, 자신의 경험과 역량을 더욱 설득력 있게 표현할 수 있습니다.

<br>

</details>

---

## 6. 실행 방법

### 사전 요구사항

- **필수**: Git, Docker Desktop(Docker Compose 포함)
- **개별 서비스 실행 시**: JDK 21, Node.js 24, uv, Python 3.14 이상

### 프로젝트 다운로드

```bash
git clone https://github.com/ITCEN-CENergy/Passed.git
cd Passed
```

### 환경 변수 설정

프로젝트 루트에 `.env` 파일을 생성하고 아래 값을 설정합니다. `JWT_SECRET`은 반드시 충분히 긴 임의의 문자열로 변경해야 하며, 실제 키가 포함된 파일은 저장소에 커밋하지 않습니다.

```dotenv
POSTGRES_DB=edu
POSTGRES_USER=edu
POSTGRES_PASSWORD=1234
JWT_SECRET=replace-with-a-long-random-secret

# OpenAI 기능과 임베딩을 사용할 경우에만 설정
# OPENAI_API_KEY=your-api-key

# 선택 사항: Grafana 관리자 계정
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
```

### 최초 데이터베이스 초기화

새로운 `postgres-data` 볼륨을 사용하는 경우에는 전체 서비스를 실행하기 전에 데이터베이스 초기화가 필요합니다. 채용공고 시드가 기존 채용공고 데이터를 참조하므로 다음 순서를 지켜야 합니다.

```text
PostgreSQL/pgvector 실행
  → 기준 Flyway 마이그레이션 적용
  → 산업·직무·회사 기준 데이터 적재
  → 채용공고 CSV 및 청크 적재
  → 나머지 Flyway 마이그레이션 적용
```

먼저 PostgreSQL을 실행합니다.

```bash
docker compose up -d postgres
docker compose ps
```

이후 [데이터베이스 초기화 가이드](./1.md#%EF%B8%8F-%EC%8B%A4%ED%96%89-%EC%A0%84-db-%EC%B4%88%EA%B8%B0%ED%99%94-%EB%B0%8F-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EC%A0%81%EC%9E%AC-%ED%95%84%EC%88%98)의 `2. 기준 스키마만 먼저 마이그레이션`부터 `5. 나머지 Flyway 적용 및 검증`까지 순서대로 진행합니다.

> 이 과정은 빈 개발 DB에서 최초 한 번만 필요합니다. 기존 DB에서는 `docker compose down -v`를 실행하지 말고, 먼저 백업과 `flyway_schema_history`를 확인하세요. 이미 적용된 Flyway 파일은 수정하거나 삭제하면 안 됩니다.

### 전체 서비스 실행

초기화가 끝난 프로젝트 루트에서 프론트엔드, 백엔드, AI, PostgreSQL 및 모니터링 서비스를 한 번에 실행합니다.

```bash
docker compose up --build -d
docker compose ps
```

전체 로그 또는 특정 서비스의 로그는 다음과 같이 확인합니다.

```bash
docker compose logs -f
docker compose logs -f backend
```

### 접속 주소

| 서비스 | URL |
| --- | --- |
| **Frontend (React/Vite)** | [`localhost:5173`](http://localhost:5173) |
| **Backend API (Spring Boot)** | [`localhost:8080`](http://localhost:8080) |
| **AI API (FastAPI)** | [`localhost:8000`](http://localhost:8000) |
| **AI Swagger** | [`localhost:8000/docs`](http://localhost:8000/docs) |
| **PostgreSQL** | `localhost:5433` |
| **Grafana** | [`localhost:3000`](http://localhost:3000) |
| **Prometheus** | [`localhost:9090`](http://localhost:9090) |

### 개별 서비스 실행

Docker Compose로 PostgreSQL만 실행한 뒤 각 서비스를 로컬에서 개별적으로 실행할 수도 있습니다.

#### PostgreSQL

```bash
docker compose up -d postgres
```

#### Backend

```bash
cd passed-backend
./gradlew bootRun
```

#### Frontend

```bash
cd passed-frontend
npm ci
npm run dev
```

#### AI

```bash
cd passed-ai
uv sync
uv run fastapi dev app/main.py
```

### 서비스 종료

```bash
# 컨테이너만 중지
docker compose stop

# 컨테이너와 네트워크 제거(DB 데이터는 유지)
docker compose down
```

DB 데이터를 포함한 모든 볼륨을 삭제하려면 다음 명령어를 사용합니다.

```bash
docker compose down -v
```

> `docker compose down -v`를 실행하면 PostgreSQL 데이터가 모두 삭제되어 복구할 수 없습니다.

---

## 7. 프로젝트 아키텍처

<img width="100%" alt="Passed 프로젝트 아키텍처" src="./passed-frontend/public/result_img/system_architecture.png" />

---

## 8. 협업 컨벤션

### Jira

<details>
<summary><strong>Jira 이슈 관리</strong></summary>

<br>

모든 개발 작업은 Jira에서 먼저 생성하고, 발급된 Jira 키를 기준으로 GitHub Issue를 만들어 Git 작업과 연결합니다.

#### 보드 운영

```text
할 일(To Do)
    ↓
진행 중(In Progress)
    ↓
완료(Done)
```

- 작업 시작 전 Jira 이슈를 생성하고 담당자를 지정합니다.
- 작업을 시작하면 상태를 `할 일`에서 `진행 중`으로 변경합니다.
- 하나의 작업을 세분화해야 할 때는 하위 작업을 생성하여 함께 관리합니다.
- 구현과 검증이 끝나고 관련 코드가 병합되면 상태를 `완료`로 변경합니다.

#### Jira와 GitHub 연동 방식

```text
1. Jira 작업 생성
        ↓
2. Jira 키 발급(COPE-129 등)
        ↓
3. Jira 키를 제목 또는 본문에 포함한 GitHub Issue 생성
        ↓
4. Jira 키로 브랜치 생성 및 Commit
        ↓
5. Pull Request에 Jira 키와 GitHub Issue 연결
        ↓
6. Merge 후 Jira 작업을 완료 상태로 변경
```

#### 작성 예시

```text
Jira
COPE-129 | README.md 템플릿 작성

GitHub Issue
#42 | [COPE-129] README.md 템플릿 작성

Branch
doc/COPE-129

Commit
COPE-129 docs: README.md 템플릿 작성
```

> 브랜치와 커밋에는 Jira 키를 사용하고, GitHub Issue와 Pull Request에도 동일한 Jira 키를 작성하여 Jira와 GitHub의 작업 내역을 연결합니다.

#### 운영 원칙

- 개발 전에 Jira 이슈를 생성합니다.
- Jira 이슈에는 작업 목적, 상세 내용, 담당자 및 필요한 하위 작업을 작성합니다.
- Jira에서 발급된 `COPE-번호`를 GitHub Issue 제목 또는 본문에 포함합니다.
- Jira 상태를 실제 개발 진행 상황과 동일하게 유지합니다.
- Pull Request에 Jira 키와 관련 GitHub Issue를 함께 연결합니다.
- 코드 병합 및 검증이 끝난 작업만 Jira에서 `완료`로 이동합니다.

</details>

### Git


<details>
<summary><strong>브랜치 전략: main - dev - feature</strong></summary>

<br>

짧은 기간 내 효율적인 관리를 위해 GitHub-flow 기반의 간소화된 브랜치 전략을 사용합니다.

```text
main
  └─ dev
      ├─ feat/COPE-30
      ├─ fix/COPE-127
      ├─ refactor/COPE-100
      └─ doc/COPE-129
```

| 브랜치 | 역할 |
| --- | --- |
| **main** | 최종 배포용 브랜치로, 언제든 즉시 서비스할 수 있는 상태를 유지합니다. |
| **dev** | 개발 통합 브랜치로, 모든 기능 작업이 모이는 기준점입니다. |
| **feature** | 기능 구현 브랜치로, `dev`에서 분기하고 작업 완료 후 `dev`로 PR을 생성합니다. |

#### 브랜치 네이밍 규칙

```text
[Prefix]/COPE-{Jira 번호}
```

예: `feat/COPE-30`, `fix/COPE-127`, `refactor/COPE-100`, `doc/COPE-129`

</details>

<details>
<summary><strong>Git 작업 흐름</strong></summary>

<br>

```text
1. Jira Issue 생성
        ↓
2. dev에서 Jira 키 기반 작업 브랜치(feat/COPE-번호 등) 생성
        ↓
3. 기능 개발 및 Commit
        ↓
4. 원격 작업 브랜치 Push
        ↓
5. dev를 대상으로 Pull Request 생성
        ↓
6. 코드 리뷰 및 승인
        ↓
7. dev Merge
        ↓
8. 마일스톤에 맞춰 검증된 dev를 main에 Merge
```

</details>

<details>
<summary><strong>커밋 컨벤션</strong></summary>

<br>

#### 커밋 메시지 형식

```text
COPE-{Jira 번호} {type}: 작업 내용
```

#### 작성 예시

```text
COPE-100 refactor: 로드맵 UI 수정
COPE-123 feat: 학습 자료 추천 품질 개선
COPE-129 docs: README.md 템플릿 작성
```

#### Prefix 종류

| Prefix | 내용 |
| --- | --- |
| **feat** | 새로운 기능 구현 |
| **fix** | 버그 해결, 오류 수정 및 코드 보정 |
| **design** | UI 레이아웃 조정 및 디자인 요소 변경 |
| **merge** | 브랜치 병합 및 충돌 해결 |
| **refactor** | 기능 변화가 없는 코드 구조 개선 |
| **docs** | README, Wiki 등 문서 추가 및 개정 |
| **chore** | 빌드 및 패키지 설정 등 프로덕션 코드 외 작업 |
| **setting** | 프로젝트 초기 설정 및 환경 구축 |
| **rename** | 파일 또는 폴더 이름 수정 및 위치 이동 |
| **remove** | 파일 삭제 작업 |
| **comment** | 주석 추가 및 변경 |

</details>

<details>
<summary><strong>저장소 운영 원칙</strong></summary>

<br>

- 새 Issue와 Pull Request 생성 시 팀에서 정의한 템플릿을 사용합니다.
- `main`과 `dev` 브랜치에는 직접 Push하지 않고 Pull Request를 통해 병합합니다.
- Pull Request에는 관련 Jira 이슈를 연결합니다.
- Pull Request에 변경 목적, 주요 구현 내용, 테스트 방법 및 결과를 작성합니다.
- 리뷰와 승인 절차를 거치고 충돌 및 테스트 결과를 확인한 뒤 병합합니다.
- `dev`에서 검증이 끝난 코드는 마일스톤에 맞춰 `main`으로 병합합니다.

</details>

### Flyway

<details>
<summary><strong>데이터베이스 마이그레이션 관리</strong></summary>

<br>

데이터베이스 스키마를 변경할 때는 **Gradle 명령어로 마이그레이션 파일을 생성하고 SQL을 작성한 다음, JPA 엔티티에도 동일한 변경 사항을 반영**합니다.

#### 마이그레이션 파일 생성

파일을 직접 생성하지 않고 프로젝트에 정의된 Gradle 명령어를 사용합니다.

```bash
cd passed-backend
./gradlew createMigration \
  -PmigrationName=create_skill_aliases
```

Windows에서는 다음 명령어를 사용합니다.

```powershell
cd passed-backend
.\gradlew.bat createMigration `
  -PmigrationName=create_skill_aliases
```

#### 파일명 규칙

```text
V{UTC_yyyyMMddHHmmssSSS}__{description}.sql
```

Gradle 작업이 현재 UTC 시각을 기반으로 버전을 생성하며, 설명은 영문 소문자와 언더스코어 형식으로 정규화됩니다.

```text
V20260803031542127__create_skill_aliases.sql
```

#### 생성 결과

```text
src/main/resources/db/migration/
└── V20260803031542127__create_skill_aliases.sql
```

#### 작업 순서

```text
1. Gradle 명령어로 Flyway SQL 파일 생성
        ↓
2. 마이그레이션 SQL 작성
        ↓
3. JPA 엔티티에 동일한 변경 사항 반영
        ↓
4. SQL과 Java 타입 일치 여부 확인
        ↓
5. 마이그레이션 실행 및 기존 데이터 보존 여부 검증
        ↓
6. Pull Request 생성
```

#### PR 전 체크리스트

- [ ] Gradle 명령어로 마이그레이션 SQL 파일을 생성했는가?
- [ ] 공유된 기존 마이그레이션 파일을 수정하지 않았는가?
- [ ] 한 번이라도 실행된 마이그레이션 파일을 수정하지 않았는가?
- [ ] Flyway SQL 변경 사항을 JPA 엔티티에도 동일하게 반영했는가?
- [ ] SQL 타입과 Java 타입이 서로 일치하는가?
- [ ] 마이그레이션 이후에도 기존 데이터가 보존되는가?

> 공유되었거나 한 번이라도 실행된 마이그레이션 파일은 절대 수정하지 않습니다. 변경이 추가로 필요하면 새로운 마이그레이션 파일을 생성합니다.

</details>

---

## 9. 트러블 슈팅

<details>
<summary><strong>강민주 - ID 기반 스킬 매칭의 한계를 보완한 원문 근거 AI 검증</strong></summary>

<br>

### 문제

- 사용자 스킬과 채용공고 스킬을 ID로만 비교하면서, 이력서와 자기소개서에 실제 수행 경험이 있어도 다른 스킬 ID로 추출된 경우 미충족으로 판정되는 문제가 발생했습니다.
- 실제 지원 가능한 채용공고가 추천 대상에서 제외되거나 적합도 점수가 낮아져 추천의 정확성과 사용자 신뢰도가 저하될 수 있었습니다.

### 원인

- 사용자 스킬 추출 과정에서는 이력서 전체를 대표하는 역량을 생성하지만, 채용공고에서는 업무별 요구사항을 더 세분화된 스킬로 표현하고 있었습니다.
- 동일하거나 유사한 수행 경험도 서로 다른 스킬 ID로 정규화될 수 있어 정확한 ID 일치만으로는 사용자의 실제 경험을 모두 반영하기 어려웠습니다.
- 반대로 임베딩 유사도만으로 충족 여부를 판단하면 자격증이나 교육 이력, 단순 키워드가 실무 경험으로 잘못 인정되는 오탐 가능성이 있었습니다.

### 해결 방안

- 1차 ID 매칭 유지: 기존 사용자 추출 스킬과 채용공고 요구 스킬을 ID 기준으로 우선 비교하여 명확하게 일치하는 역량을 판정했습니다.
- 미충족 스킬 원문 검색: ID 매칭에서 미충족으로 판정된 채용공고 스킬만 대상으로 이력서·자기소개서 원문 청크를 임베딩 검색했습니다.
- LLM 행동 근거 검증: 검색된 원문이 목표 스킬과 관련된 구체적인 수행·완료 행동을 입증하는지 GPT-4 계열 모델로 검증했습니다.
- 목표 스킬 레벨 재산정: 유사한 기존 사용자 스킬의 레벨을 복사하지 않고, 확인된 원문 행동을 기준으로 목표 스킬의 숙련도를 새롭게 산정했습니다.
- 추천 실행 범위 내 임시 반영: 검증된 스킬은 사용자 스킬 데이터에 영구 저장하지 않고, 해당 추천 실행에서만 DIRECT_DOCUMENT_EVIDENCE 유형의 임시 충족 스킬로 반영했습니다.
- 안전장치 적용: 벡터 유사도는 후보 검색에만 사용하고 원문 행동 근거, 스킬 카테고리 호환성, 직접 인용 검증을 모두 통과한 경우에만 충족 처리했습니다.
- 검증: 개인정보 보호 교육 수강과 같은 단순 학습 이력은 미충족으로 유지하고, “개인정보를 탐지하고 차단하는 보안 필터를 적용”한 수행 경험은 정보보호 의식의 직접 근거로 인정했습니다. 또한 자격증만으로 기술·실무 경험을 충족하거나 챗봇 경험을 음성 AI 경험으로 판단하는 등의 오탐 사례를 테스트했습니다.

### 결과

- 변경 전에는 스킬 ID가 일치하지 않으면 실제 수행 경험이 원문에 존재하더라도 미충족으로 판정되었습니다.
- 변경 후에는 구체적인 수행 행동이 확인된 유사 경험을 추천에 반영하면서도, 단순 키워드·교육·자격증에 의한 잘못된 매칭을 차단할 수 있게 되었습니다.
- 검증 데이터 기준 상위 20개 추천 정확도는 40.0%에서 88.0%로 향상되었고, 과대 추천률은 60.0%에서 2.0%로 감소했습니다.
- 사용자 스킬 추출 기능을 변경하지 않고, 추천 서비스가 요구하는 정보 범위에 맞춰 원문 교차 검증 계층을 추가하여 각 기능의 책임을 유지했습니다.

<br>

</details>

<details>
<summary><strong>신재욱 - 비동기 병렬 처리를 통한 학습자료 검색 성능 개선</strong></summary>

<br>

### 문제

- 여러 역량의 학습자료를 순차적으로 검색하면서 로드맵 생성 응답이 지연되었습니다.
- 외부 검색 공급자 하나에서 타임아웃이 발생하면 전체 생성 과정이 함께 실패할 위험이 있었습니다.
- 실제 측정 결과 학습자료 검색에 약 24.7초, 전체 요청 처리에 약 37.6초가 소요되었습니다.

### 원인

- 역량별 검색과 K-MOOC, Kakao 도서, 웹 검색 등 외부 공급자 호출을 순차적으로 처리하고 있었습니다.
- 외부 API 호출마다 클라이언트를 반복 생성해 HTTP 연결이 재사용되지 않았습니다.
- 전체 요청에 대한 제한 시간과 하위 작업 취소 처리가 명확하게 구성되지 않았습니다.

### 해결 방안

- **비동기 병렬화**: 역량별 검색과 공급자별 API 호출을 `asyncio.gather` 기반으로 병렬 처리했습니다.
- **동시성 제한**: 세마포어를 적용해 동시에 실행되는 외부 요청 수를 제한하고 API 과부하를 방지했습니다.
- **연결 재사용**: 애플리케이션 생명주기 동안 공유하는 `HTTPX AsyncClient`를 사용했습니다.
- **부분 실패 허용**: 일부 공급자에서 오류나 타임아웃이 발생해도 정상 공급자의 검색 결과로 로드맵 생성을 계속하도록 구성했습니다.
- **타임아웃 및 취소 처리**: 전체 생성 deadline을 적용하고, 제한 시간을 초과하면 실행 중인 하위 작업까지 취소되도록 구현했습니다.

### 결과

- 학습자료 검색 시간을 **24.676초에서 9.571초로 단축하여 61.2% 개선**했습니다.
- 전체 요청 시간을 **37.553초에서 23.578초로 단축하여 37.2% 개선**했습니다.
- 검색 후보 31개와 설명 길이를 동일하게 유지해 결과를 축소하지 않고 처리 성능을 높였습니다.
- 외부 공급자의 부분 실패가 전체 로드맵 생성 실패로 이어지지 않도록 안정성을 개선했습니다.

> 해당 수치는 동일 조건에서 수행한 초기 실측 결과이며, 운영 성능을 확정하려면 반복 측정과 부하 테스트가 추가로 필요합니다.

<br>

</details>

<details>
<summary><strong>이민호 - 생성형 AI 응답 형식 불일치로 인한 자기소개서 분석 실패 방지</strong></summary>

<br>

### 문제

- 자기소개서 전체 분석 과정에서 AI가 strengths와 improvements를 문자열이 아닌 배열이나 중첩 객체로 반환했습니다.
- Pydantic 모델 검증 단계에서 Input should be a valid string 오류가 발생해 정상적으로 생성된 분석 결과도 사용자에게 제공되지 못했습니다.

### 원인

- 응답 스키마와 프롬프트에 문자열 형식을 선언하면 AI가 항상 같은 형식을 반환할 것이라고 가정했습니다.
- 외부 AI 응답을 애플리케이션 DTO로 바로 검증해 비결정적인 응답 형식을 흡수할 경계 계층이 없었습니다.

### 해결 방안

- **오류 응답 재현**: 배열과 중첩 객체가 반환되는 사례를 기준으로 Pydantic 검증 실패 조건을 재현했습니다.
- **서비스 경계 정규화**: _feedback_to_text()를 구현해 문자열은 그대로 유지하고, 배열은 줄바꿈 텍스트로 결합하며, 중첩 객체도 안전하게 문자열로 변환했습니다.
- **검증 순서 변경**: AI 원본 응답을 먼저 정규화한 후 OverallReviewOutput 모델로 검증하도록 처리 순서를 변경했습니다.
- **검증**: 배열·객체 응답에 대한 회귀 테스트를 추가하고 tests/test_coverletter_service.py 테스트 5개를 통과시켰습니다.

### 결과

- 변경 전에는 AI 응답의 일부 필드만 형식에서 벗어나도 전체 자기소개서 분석이 실패했습니다.
- 변경 후에는 문자열·배열·객체 형태의 응답을 일관된 텍스트 계약으로 변환할 수 있게 되었습니다.
- 외부 AI 모델의 비결정성이 서비스 장애로 직접 전파되지 않도록 API 경계를 강화했습니다.

<br>

</details>

<details>
<summary><strong>조윤지 - 단일 스킬 추출 구조의 오탐·누락을 단계별 검증 파이프라인으로 개선</strong></summary>

<br>

### 문제

- 초기에는 LLM이 청크 원문에서 스킬 후보를 한 번 추출하고 마스터 스킬에 매핑한 결과를 바로 저장했습니다.
- 원문에 포함된 스킬이 누락되거나, 의미상 관련만 있는 마스터 스킬이 실제 보유 역량으로 저장되는 사례가 함께 발생했습니다.
- 추출과 매핑, 원문 근거 검증 결과가 하나의 최종 점수에 섞여 있어 프롬프트와 매핑 로직 중 어느 단계가 문제인지 구분하기 어려웠습니다.

### 원인

- 검증 1은 LLM이 생성한 후보에 의존하므로 처음 추출하지 못한 스킬을 이후 매핑 단계에서 복구할 수 없었습니다.
- 임베딩 유사도는 문장과 스킬이 의미상 가깝다는 검색 신호일 뿐, 사용자가 해당 역량을 실제로 보유한다는 근거는 아니었습니다.
- 다양한 추출 표현을 이름 임베딩만으로 연결하면 구체적인 기술명과 추상적인 경험·행동 특성의 점수 분포 차이로 잘못된 마스터가 선택될 수 있었습니다.

### 해결 방안

- **단계별 골든셋 구축**: 추출 34개 문서·정답 후보 69개, 매핑 53건(`MAP` 47건·`NO_MATCH` 6건), 원문 근거 검증 111건을 분리해 추출·매핑·검증 오류를 독립적으로 측정했습니다.
- **보수적인 마스터 매핑**: 이름-only 방식 대신 `EXACT → NORMALIZED → ALIAS → EMBEDDING` 순서를 적용하고, 카테고리 일치·최소 유사도·상위 후보 간 margin을 통과한 경우에만 자동 매핑했습니다.
- **Hybrid Retrieval 도입**: 저장된 청크 임베딩과 문장 단위 임베딩을 각각 마스터 스킬과 비교하고, 같은 `skill_id`는 가장 높은 유사도만 유지했습니다. 카테고리 균형과 유사도 기준을 적용한 청크별 Top-40 후보만 후속 검증에 전달했습니다.
- **Strict 검증 1·2**: 검증 1 결과의 오탐을 먼저 제거하고, Hybrid Retrieval 후보 중 기술명·활성 별칭의 직접 명시 또는 관찰 가능한 완료 행동이 원문에서 확인된 스킬만 검증 2로 복구했습니다. 미래 계획, 근거 없는 자기평가, 자격증 문맥을 기술 사용 경험으로 해석한 결과는 제외했습니다.
- **근거 중심 저장**: 최종 승인 결과를 `skill_id`로 병합하되 서로 다른 청크의 근거는 모두 보존하고, 저장 직전 청크 소유권과 `content_hash`를 검증해 분석 도중 변경된 문서가 반영되지 않도록 했습니다.

### 결과

- 이름 임베딩만 사용한 매핑 실험의 Top-1 정확도가 **8/30(26.7%)**에 머문 결과를 바탕으로, 정확·정규화·별칭 매핑을 우선하고 임베딩을 보조 수단으로 사용하는 전략을 확정했습니다.
- 사용자 56의 Strict 검증 골든셋 111건 중 `REVIEW` 1건을 제외한 110건 평가에서 **Precision 100%, Recall 69.1%, F1 81.8%, REJECT 정확도 100%, 오탐률 0%**를 기록했습니다.
- 최종 저장 전에 Pass 1 오탐을 제거하면서도 Pass 2가 누락 스킬과 추가 근거를 복구하도록 구성해, 추천 점수를 왜곡하는 잘못된 스킬 저장을 줄이고 분석 결과의 설명 가능성을 높였습니다.

> 성능 수치는 고정된 사용자 문서와 골든셋을 사용한 실험 결과이며, 운영 전체 사용자를 대표하는 수치로 확정하려면 다양한 문서에 대한 추가 평가가 필요합니다.

<br>

</details>

---

## 10. 시연 화면

### 인증 관련

<table>
  <tr>
    <td width="50%" align="center"><strong>회원가입</strong><br><img width="100%" alt="회원가입 화면" src="./passed-frontend/public/result_img/signup.png" /></td>
    <td width="50%" align="center"><strong>로그인</strong><br><img width="100%" alt="로그인 화면" src="./passed-frontend/public/result_img/login.png" /></td>
  </tr>
</table>

### 사용자 정보 입력 및 스킬 추출

<table>
  <tr>
    <td width="50%" align="center"><strong>관심 직무 선택</strong><br><img width="100%" alt="관심 직무 선택 화면" src="./passed-frontend/public/result_img/job_select.png" /></td>
    <td width="50%" align="center"><strong>이력서 작성</strong><br><img width="100%" alt="이력서 작성 화면" src="./passed-frontend/public/result_img/resume_write.png" /></td>
  </tr>
  <tr>
    <td width="50%" align="center"><strong>기본 자기소개서 작성</strong><br><img width="100%" alt="기본 자기소개서 작성 화면" src="./passed-frontend/public/result_img/base_cover_letter_write.png" /></td>
    <td width="50%" align="center"><strong>사용자 스킬 추출</strong><br><img width="100%" alt="사용자 스킬 추출 화면" src="./passed-frontend/public/result_img/user_skill_create.png" /></td>
  </tr>
</table>

### 채용공고 추천

<table>
  <tr>
    <td width="50%" align="center"><strong>맞춤 공고 추천</strong><br><img width="100%" alt="맞춤 채용공고 추천 화면" src="./passed-frontend/public/result_img/main_recommendation.png" /></td>
    <td width="50%" align="center"><strong>적합도 분석</strong><br><img width="100%" alt="채용공고 적합도 분석 화면" src="./passed-frontend/public/result_img/result_report.png" /></td>
  </tr>
  <tr>
    <td width="50%" align="center"><strong>적합도 분석 1</strong><br><img width="100%" alt="채용공고 적합도 분석 화면 1" src="./passed-frontend/public/result_img/result_report2.png" /></td>
    <td width="50%" align="center"><strong>적합도 분석 2</strong><br><img width="100%" alt="채용공고 적합도 분석 화면 2" src="./passed-frontend/public/result_img/result_report3.png" /></td>
  </tr>
</table>

### 채용공고 검색 및 조회

<table>
  <tr>
    <td colspan="2" align="center"><strong>채용공고 검색</strong><br><img width="100%" alt="채용공고 검색 화면" src="./passed-frontend/public/result_img/job_posting_search.png" /></td>
  </tr>
  <tr>
    <td width="50%" align="center"><strong>채용공고 상세 조회 1</strong><br><img width="100%" alt="채용공고 상세 조회 화면 1" src="./passed-frontend/public/result_img/job_posting_detail.png" /></td>
    <td width="50%" align="center"><strong>채용공고 상세 조회 2</strong><br><img width="100%" alt="채용공고 상세 조회 화면 2" src="./passed-frontend/public/result_img/job_posting_detail2.png" /></td>
  </tr>
</table>

### 학습 로드맵

<table>
  <tr>
    <td width="50%" align="center"><strong>로드맵 공고 바구니</strong><br><img width="100%" alt="학습 로드맵 채용공고 바구니 화면" src="./passed-frontend/public/result_img/roadmap_job_posting_basket.png" /></td>
    <td width="50%" align="center"><strong>학습 로드맵</strong><br><img width="100%" alt="학습 로드맵 메인 화면" src="./passed-frontend/public/result_img/roadmap_main.png" /></td>
  </tr>
  <tr>
    <td width="50%" align="center"><strong>마일스톤 학습</strong><br><img width="100%" alt="학습 로드맵 마일스톤 화면" src="./passed-frontend/public/result_img/roadmap_milestone.png" /></td>
    <td width="50%" align="center"><strong>로드맵 재계획</strong><br><img width="100%" alt="학습 로드맵 재계획 화면" src="./passed-frontend/public/result_img/roadmap_replan.png" /></td>
  </tr>
</table>

### 자기소개서 첨삭

<table>
  <tr>
    <td width="50%" align="center"><strong>AI 자기소개서 작성</strong><br><img width="100%" alt="AI 자기소개서 작성 화면" src="./passed-frontend/public/result_img/ai_cover_letter_write.png" /></td>
    <td width="50%" align="center"><strong>AI 자기소개서 피드백</strong><br><img width="100%" alt="AI 자기소개서 피드백 화면" src="./passed-frontend/public/result_img/ai_cover_letter_feedback.png" /></td>
  </tr>
</table>

### 채용공고 등록

<img width="100%" alt="채용공고 등록 화면" src="./passed-frontend/public/result_img/job_posting_write.png" />

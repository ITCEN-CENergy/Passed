# Passed 프로젝트

Passed 프로젝트는 프론트엔드(React/Vite), 백엔드(Spring Boot), AI 서비스(FastAPI)로 구성된 애플리케이션입니다.
본 프로젝트의 로컬 개발 환경은 **Docker Compose**를 통해 하나로 통합되어 있어, 매우 쉽게 전체 시스템을 구동할 수 있습니다.

## 📋 사전 요구 사항 (Prerequisites)

이 프로젝트를 로컬에서 구동하기 위해 다음 도구가 필요합니다:
- **Docker Desktop** (또는 Docker Engine & Docker Compose 플러그인)
- *로컬 직접 개발 시 (선택사항)*: Node.js, Java(JDK 21), uv(Python 환경 관리)

---

## 🗄️ 실행 전 DB 초기화 및 데이터 적재 (필수)

`compose.yaml`은 PostgreSQL이 준비되면 백엔드를 바로 시작합니다. 그러나 채용공고 CSV와 참조 데이터는 별도 적재해야 하며, 빈 DB에서 모든 Flyway 마이그레이션을 한 번에 실행하면 `V20260804154936000__seed_job_posting_skills.sql`이 아직 없는 채용공고를 참조하여 외래 키 오류가 발생할 수 있습니다.

따라서 **새 개발 DB는 아래 순서대로 준비한 뒤** 전체 서비스를 실행합니다.

```text
PostgreSQL/pgvector
  → 기준 스키마 Flyway (V20260804151714560까지)
  → 산업·직무 및 회사 기준 데이터
  → 채용공고 CSV + 청크 적재
  → 나머지 Flyway (채용공고-스킬 시드 포함)
  → 애플리케이션 실행 및 검증
```

> 이 절차는 `postgres-data` 볼륨을 새로 만든 **빈 개발 DB 전용**입니다. 기존 DB에는 `docker compose down -v`를 실행하지 말고 먼저 백업 및 `flyway_schema_history` 상태를 확인하세요. 이미 적용된 Flyway 파일은 수정하거나 삭제하면 안 됩니다.

### 1. 환경 변수와 PostgreSQL 준비

프로젝트 루트의 `.env`에 최소한 `JWT_SECRET`을 설정합니다. 값은 저장소에 커밋하지 않습니다. OpenAI 기반 기능과 임베딩까지 사용할 경우에만 `OPENAI_API_KEY`를 추가합니다.

```dotenv
JWT_SECRET=replace-with-a-long-random-secret
# OPENAI_API_KEY=sk-...
```

그런 다음 DB만 기동합니다.

```powershell
docker compose up -d postgres
docker compose ps
```

DB 이름·사용자·비밀번호는 루트 `.env`의 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`를 사용합니다.

### 2. 기준 스키마만 먼저 마이그레이션

먼저 실행 중인 PostgreSQL 컨테이너에서 실제 접속 정보를 읽고 연결 상태를 확인합니다. 비밀번호는 화면에 출력하지 않습니다.

```powershell
$postgresContainerId = docker compose ps -q postgres
if (-not $postgresContainerId) { throw 'postgres 컨테이너를 찾지 못했습니다. docker compose up -d postgres를 먼저 실행하세요.' }

$dbName = docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_DB"'
$dbUser = docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_USER"'
$dbPassword = docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_PASSWORD"'
if (-not $dbName -or -not $dbUser -or -not $dbPassword) { throw 'PostgreSQL 접속 환경 변수를 읽지 못했습니다.' }

docker compose exec -T postgres pg_isready -U $dbUser -d $dbName
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName `
  -c 'SELECT current_user, current_database();'
```

아래 명령은 실행 중인 PostgreSQL 컨테이너의 네트워크 네임스페이스를 직접 공유해 `V20260804151714560`까지 적용합니다. 따라서 Compose 네트워크 이름이나 호스트 포트 공개 상태에 의존하지 않습니다.

```powershell
docker run --rm `
  --network "container:$postgresContainerId" `
  -v "${PWD}\passed-backend\src\main\resources\db\migration:/flyway/sql:ro" `
  flyway/flyway:11 `
  -locations="filesystem:/flyway/sql" `
  "-url=jdbc:postgresql://127.0.0.1:5432/$dbName" `
  "-user=$dbUser" "-password=$dbPassword" `
  -target=20260804151714560 `
  -connectRetries=60 migrate
```

명령이 끝난 뒤 현재 PowerShell 세션에 남은 비밀번호는 전체 초기화가 끝나면 `Remove-Variable dbPassword`로 제거합니다.

### 3. CSV가 참조하는 기준 데이터 적재

기준 스키마가 준비된 뒤에 산업 21건, 직무 239건과 CSV가 참조하는 회사 ID `0`~`159`를 넣습니다. 두 SQL은 재실행 가능하도록 작성되어 있으나, 운영 DB에는 개발용 회사 시드를 실행하지 않습니다.

```powershell
Get-Content -Raw .\passed-ai\embedding-data\job-posting\schema\seed_industries_job_roles_from_excel.sql |
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName

Get-Content -Raw .\passed-ai\embedding-data\job-posting\schema\dev_seed_companies_0_159.sql |
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName
```

### 4. 채용공고 CSV와 청크 적재

`job_postings.csv`는 4,730개 공고(ID `1`~`4730`)이며, 회사 ID `0`~`159`, 직무 ID `1`~`239`를 참조합니다. 로더는 `job_postings`를 명시적 ID로 UPSERT하고 변경된 공고의 원문 청크를 동기화합니다. CSV는 UTF-8을 우선 시도하고 필요하면 CP949로 다시 읽습니다.

```powershell
Set-Location .\passed-ai
uv sync

Set-Location .\embedding-data\job-posting
$escapedDbPassword = [Uri]::EscapeDataString($dbPassword)
$env:DATABASE_URL = "postgresql://${dbUser}:${escapedDbPassword}@localhost:5433/${dbName}"
uv run --project ..\.. python -m job_posting_pipeline.run_loader .\data\job_postings.csv

Set-Location ..\..\..
```

### 5. 나머지 Flyway 적용 및 검증

이제 채용공고 부모 행이 준비되었으므로, `job_posting_skills` 시드를 포함한 나머지 마이그레이션을 적용합니다.

```powershell
docker run --rm `
  --network "container:$postgresContainerId" `
  -v "${PWD}\passed-backend\src\main\resources\db\migration:/flyway/sql:ro" `
  flyway/flyway:11 `
  -locations="filesystem:/flyway/sql" `
  "-url=jdbc:postgresql://127.0.0.1:5432/$dbName" `
  "-user=$dbUser" "-password=$dbPassword" `
  -connectRetries=60 migrate

docker compose exec -T postgres psql -U $dbUser -d $dbName -c "
  SELECT
    (SELECT COUNT(*) FROM industries) AS industries,
    (SELECT COUNT(*) FROM job_roles) AS job_roles,
    (SELECT COUNT(*) FROM companies WHERE id BETWEEN 0 AND 159) AS companies,
    (SELECT COUNT(*) FROM job_postings) AS job_postings,
    (SELECT COUNT(*) FROM flyway_schema_history WHERE success) AS applied_migrations;
"

Remove-Item Env:DATABASE_URL -ErrorAction SilentlyContinue
Remove-Variable escapedDbPassword, dbPassword -ErrorAction SilentlyContinue
```

기대값은 `industries=21`, `job_roles=239`, `companies=160`, `job_postings=4730`입니다. 이후 `docker compose logs backend`에서 Flyway 성공과 JPA 검증 완료를 확인합니다.

### 6. 임베딩 적재는 별도 선택 작업

임베딩은 OpenAI API를 호출해 비용이 발생하므로 자동 Compose 시작 과정에 포함하지 않습니다. API 키와 비용 승인이 준비된 경우에만 작은 배치로 먼저 확인한 뒤 전체 작업을 실행합니다.

```powershell
Set-Location .\passed-ai\embedding-data\job-posting
$dbPassword = docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_PASSWORD"'
$escapedDbPassword = [Uri]::EscapeDataString($dbPassword)
$env:DATABASE_URL = "postgresql://${dbUser}:${escapedDbPassword}@localhost:5433/${dbName}"
$env:OPENAI_API_KEY = 'your-api-key'

# 소규모 확인 후 로그의 failed=0, remaining 값을 점검합니다.
uv run --project ..\.. python -m job_posting_pipeline.run_embedding --max-iterations 1 --batch-size 100

# 전체 적재는 비용과 결과를 확인한 뒤에만 실행합니다.
uv run --project ..\.. python -m job_posting_pipeline.run_embedding --batch-size 100
Set-Location ..\..\..

Remove-Item Env:DATABASE_URL, Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
Remove-Variable escapedDbPassword, dbPassword -ErrorAction SilentlyContinue
```

---

## 🚀 개발 환경 구동 방법 (One-Click Setup)

프로젝트 최상위 디렉토리에 있는 `compose.yaml` 파일에는 프론트엔드, 백엔드, AI 서비스, 데이터베이스, 모니터링 도구까지 개발에 필요한 모든 컨테이너 설정이 포함되어 있습니다.

1. 위의 **DB 초기화 및 데이터 적재**를 완료한 프로젝트 루트 디렉토리에서 터미널을 엽니다.
2. 다음 명령어를 실행하여 모든 서비스를 빌드하고 백그라운드에서 구동합니다.
   ```bash
   docker compose up --build -d
   ```
3. 각 컨테이너가 정상적으로 실행되었는지 확인합니다 (`Up` 상태 확인).
   ```bash
   docker compose ps
   ```
4. 실시간 로그가 필요할 경우 다음 명령어를 사용합니다.
   ```bash
   docker compose logs -f
   ```
   *(특정 서비스 로그만 보려면 `docker compose logs -f backend` 와 같이 서비스명을 뒤에 붙입니다.)*

---

## 🌐 접속 정보 (Port Mapping)

`docker compose up` 명령어가 성공적으로 완료되면 다음 주소를 통해 각 서비스에 접근할 수 있습니다.

- **프론트엔드 (React/Vite)**: [http://localhost:5173](http://localhost:5173)
  - 소스 코드가 볼륨 마운트되어 있어 `passed-frontend` 폴더의 코드를 수정하면 즉시 반영(Hot Reload)됩니다.
- **백엔드 API (Spring Boot)**: [http://localhost:8080](http://localhost:8080)
- **AI 서비스 API (FastAPI)**: [http://localhost:8000](http://localhost:8000)
  - Swagger API 문서: [http://localhost:8000/docs](http://localhost:8000/docs)
- **데이터베이스 (PostgreSQL)**: `localhost:5433` (컨테이너 내부 5432)
  - DB명·계정·비밀번호: 루트 `.env`의 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` 사용
- **모니터링 대시보드 (Grafana)**: [http://localhost:3000](http://localhost:3000)
  - 기본 계정: `admin` / 비밀번호: `admin`
- **모니터링 메트릭 (Prometheus)**: [http://localhost:9090](http://localhost:9090)

---

## 🛑 구동 종료 및 데이터 초기화

개발 서버 구동을 멈추고 싶다면 아래 명령어를 사용합니다.

- 컨테이너 중지:
  ```bash
  docker compose stop
  ```
- 컨테이너 및 네트워크 삭제 (데이터는 보존됨):
  ```bash
  docker compose down
  ```
- **완전 초기화 (DB 데이터 포함 모든 볼륨 삭제)**:
  ```bash
  docker compose down -v
  ```
  *(주의: DB에 저장된 모든 데이터가 날아갑니다)*

---

## 💻 개별 서비스 로컬 구동 (옵션)

만약 Docker 컨테이너가 아닌 로컬 호스트에서 직접 디버거를 연결하여 개발하고 싶다면, `docker compose`로 데이터베이스(`postgres`)만 띄워둔 후 각 서비스를 별도 구동할 수 있습니다.

1. **DB만 구동**: `docker compose up -d postgres`
2. **백엔드**: `cd passed-backend` -> `./gradlew bootRun` (또는 IDE 실행)
3. **프론트엔드**: `cd passed-frontend` -> `npm install` -> `npm run dev`
4. **AI**: `cd passed-ai` -> `uv sync` -> `uv run fastapi dev app/main.py`

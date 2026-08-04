# passed-backend

Flyway와 JPA를 함께 사용하는 이유, 실행 순서, 스키마 변경 원칙은
[Flyway + JPA 사용 보고서](docs/flyway-and-jpa.md)에서 확인할 수 있습니다.

## 개발 환경

- Java 21
- Docker Desktop
- PostgreSQL 17 + pgvector
- Spring Boot
- Flyway
- JPA/Hibernate

이 프로젝트에서는 **Flyway가 DB 테이블과 인덱스를 생성**하고, JPA/Hibernate는
`ddl-auto: validate`를 사용하여 엔티티와 실제 DB 스키마가 일치하는지 검증합니다.
JPA가 테이블을 자동 생성하거나 수정하지 않습니다.

## 로컬 DB 최초 구축

### 1. Docker Desktop 실행

Docker Desktop을 먼저 실행하고 Docker Engine이 준비될 때까지 기다립니다.

```bash
docker --version
docker compose version
```

### 2. 프로젝트 디렉터리로 이동

```bash
cd passed-backend
```

### 3. PostgreSQL + pgvector 실행

```bash
docker compose up -d --wait
```

이 명령은 다음 작업을 수행합니다.

- `pgvector/pgvector:pg17` 이미지 다운로드
- PostgreSQL 컨테이너 생성 및 실행
- `passed-backend_passed-postgres-data` Docker 볼륨 생성
- `edu` 데이터베이스와 `edu` 사용자 생성
- PostgreSQL이 연결 가능한 상태가 될 때까지 대기

실행 상태는 다음 명령으로 확인합니다.

```bash
docker compose ps
```

`postgres` 서비스의 상태가 `healthy`이면 정상입니다.

### 4. Spring Boot 실행 및 테이블 생성

```bash
./gradlew bootRun
```

애플리케이션이 시작되면 Flyway가
`src/main/resources/db/migration`의 SQL 파일을 버전 순서대로 실행합니다.

최초 실행 시 로그에 다음과 같은 내용이 출력됩니다.

```text
Migrating schema "public" to version "1 - enable pgvector"
...
Successfully applied 7 migrations
Started PassedBackendApplication
```

`Started PassedBackendApplication`이 출력되면 실행이 완료된 것입니다.
`bootRun`은 웹 서버를 계속 실행하는 명령이므로 터미널이 종료되지 않는 것이 정상입니다.
서버를 종료하려면 `Ctrl+C`를 누릅니다.

두 번째 실행부터는 이미 적용된 마이그레이션을 다시 실행하지 않습니다.

```text
Schema "public" is up to date. No migration necessary.
```

## 로컬 DB 접속 정보

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5433` |
| Database | `edu` |
| Username | `edu` |
| Password | `1234` |
| Schema | `public` |

컨테이너 내부의 PostgreSQL 포트는 `5432`지만, 로컬 PC에는 `5433`으로 연결됩니다.
DBeaver와 Spring Boot에서는 컨테이너 이름이 아니라 `localhost:5433`을 사용합니다.

### DBeaver 연결

DBeaver에서 PostgreSQL 연결을 생성하고 다음 값을 입력합니다.

```text
Host: localhost
Port: 5433
Database: edu
Username: edu
Password: 1234
```

연결 후 아래 경로에서 생성된 테이블을 확인합니다.

```text
edu > Databases > edu > Schemas > public > Tables
```

테이블이 바로 표시되지 않으면 `public` 또는 `Tables`에서 새로고침합니다.

## DB 생성 결과 확인

터미널에서 PostgreSQL에 접속할 수도 있습니다.

```bash
docker compose exec postgres psql -U edu -d edu
```

접속 후 다음 쿼리로 테이블, pgvector, Flyway 실행 이력을 확인합니다.

```sql
-- 생성된 테이블 확인
\dt

-- 현재 접속한 DB 확인
SELECT current_database();

-- pgvector 확장 확인
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

-- 적용된 마이그레이션 확인
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 벡터 인덱스 확인
SELECT tablename, indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname LIKE '%embedding_hnsw'
ORDER BY tablename, indexname;
```

psql에서 나오려면 다음을 입력합니다.

```text
\q
```

## 종료 및 재실행

### 컨테이너 종료 — DB 데이터 유지

```bash
docker compose down
```

다시 실행하면 기존 볼륨과 데이터를 그대로 사용합니다.

```bash
docker compose up -d --wait
./gradlew bootRun
```

### DB 완전 초기화 — 데이터 삭제

> 다음 명령은 로컬 PostgreSQL 볼륨과 그 안의 모든 데이터를 삭제합니다.

```bash
docker compose down -v
docker compose up -d --wait
./gradlew bootRun
```

초기 마이그레이션을 깨끗한 DB에서 다시 검증해야 하거나 로컬 테스트 데이터를
모두 삭제해도 되는 경우에만 `docker compose down -v`를 사용합니다.

## 환경 변수로 접속 정보 변경

기본 접속 정보는 `application.yaml`에 로컬 개발용 기본값으로 설정되어 있습니다.
다른 PostgreSQL을 사용할 때는 환경 변수로 덮어씁니다.

```bash
DB_URL=jdbc:postgresql://localhost:5432/edu \
DB_USERNAME=edu \
DB_PASSWORD=1234 \
./gradlew bootRun
```

로컬 `5433` 포트를 다른 프로그램이 사용 중이면 Docker의 호스트 포트를 변경할 수 있습니다.

```bash
DB_PORT=5434 docker compose up -d --wait
DB_URL=jdbc:postgresql://localhost:5434/edu ./gradlew bootRun
```

## DB 스키마 변경 방법

### 핵심 원칙

마이그레이션 파일이 팀에 공유되었거나 한 번이라도 DB에 적용된 이후에는
기존 `V1`~`V7` 파일을 수정하거나 삭제하지 않습니다.

Flyway는 적용된 SQL 파일의 checksum을 저장합니다. 적용된 파일을 나중에 수정하면
애플리케이션 실행 시 `Migration checksum mismatch` 오류가 발생합니다.

스키마를 변경할 때는 항상 다음 순서로 작업합니다.

1. `createMigration` 태스크로 새로운 SQL 파일을 생성합니다.
2. 생성된 파일에 변경 SQL을 작성합니다.
3. SQL에서 테이블, 컬럼, 제약조건, 인덱스를 변경합니다.
4. 변경된 스키마에 맞춰 JPA 엔티티를 수정하거나 생성합니다.
5. 깨끗한 DB 또는 테스트 DB에서 마이그레이션과 JPA 검증을 실행합니다.
6. SQL과 엔티티를 같은 PR에 포함합니다.

여러 팀이 동시에 작업할 때 순차 버전이 겹치지 않도록 UTC 밀리초 타임스탬프를 사용합니다.

```text
V{UTC_yyyyMMddHHmmssSSS}__{task_id}_{description}.sql
```

파일명은 직접 작성하지 않고 다음 명령으로 생성합니다.

```bash
./gradlew createMigration \
  -PmigrationName=passed_123_add_summary_to_cover_letters
```

생성 결과 예시는 다음과 같습니다.

```text
src/main/resources/db/migration/
V20260803092516379__passed_123_add_summary_to_cover_letters.sql
```

- 타임스탬프는 파일을 생성한 순간의 UTC 시각입니다.
- 입력한 이름은 소문자 `snake_case`로 자동 정규화됩니다.
- 버전과 설명 사이는 Flyway 규칙에 따라 `__`가 사용됩니다.
- 작업 ID가 없다면 `-PmigrationName=add_summary_to_cover_letters`처럼 사용할 수 있습니다.

### 기존 테이블에 필드 추가

예를 들어 `cover_letters`에 `summary` 필드를 추가한다면 새 파일을 생성합니다.

```sql
-- V20260803092516379__passed_123_add_summary_to_cover_letters.sql
ALTER TABLE cover_letters
    ADD COLUMN summary TEXT;
```

그다음 `CoverLetter` 엔티티에 같은 컬럼을 추가합니다.

```java
@Column(name = "summary", columnDefinition = "text")
private String summary;
```

필드 타입은 SQL과 엔티티가 반드시 일치해야 합니다.

| PostgreSQL | Java/JPA 예시 |
|---|---|
| `BIGINT` | `Long` |
| `INT` | `Integer` 또는 `int` |
| `BOOLEAN` | `Boolean` 또는 `boolean` |
| `VARCHAR`, `TEXT` | `String` |
| `NUMERIC(p, s)` | `BigDecimal` + `precision`, `scale` |
| `DATE` | `LocalDate` |
| `TIMESTAMPTZ` | `OffsetDateTime` |
| `JSONB` | `@JdbcTypeCode(SqlTypes.JSON)` |
| `VECTOR(1536)` | `float[]` + `@JdbcTypeCode(SqlTypes.VECTOR)` |

데이터가 이미 있는 테이블에 `NOT NULL` 컬럼을 추가할 때는 기존 행도 고려해야 합니다.

```sql
ALTER TABLE cover_letters
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'DRAFT';
```

기본값 없이 `NOT NULL` 컬럼을 바로 추가하면 기존 데이터 때문에 마이그레이션이 실패할 수 있습니다.

### 기존 필드 수정 또는 삭제

필드 타입 변경이나 삭제도 새로운 마이그레이션으로 처리합니다.

```sql
-- 길이 변경
ALTER TABLE cover_letters
    ALTER COLUMN summary TYPE VARCHAR(1000);

-- 더 이상 사용하지 않는 필드 삭제
ALTER TABLE cover_letters
    DROP COLUMN legacy_field;
```

컬럼을 삭제하거나 타입을 변경할 때는 기존 데이터 손실과 애플리케이션 호환성을 먼저 확인합니다.
운영 데이터가 있다면 한 번에 삭제하기보다 새 컬럼 추가 → 데이터 이전 → 코드 전환 → 기존 컬럼 삭제처럼
여러 마이그레이션으로 나누는 것이 안전합니다.

### 새로운 테이블 생성

새 테이블은 `CREATE TABLE`과 필요한 제약조건·인덱스를 함께 작성합니다.

```sql
-- V9__create_interviews.sql
CREATE TABLE interviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_interview_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_interview_title
        CHECK (BTRIM(title) <> '')
);

CREATE INDEX idx_interviews_user_id
    ON interviews(user_id);
```

그리고 SQL과 동일한 테이블명, 컬럼명, nullable 조건으로 JPA 엔티티를 생성합니다.

```java
@Getter
@Entity
@Table(
        name = "interviews",
        indexes = @Index(name = "idx_interviews_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 200, nullable = false)
    private String title;
}
```

### vector 필드와 인덱스 추가

pgvector 확장은 `V1__enable_pgvector.sql`에서 이미 활성화되므로 다시 생성할 필요가 없습니다.

```sql
ALTER TABLE interviews
    ADD COLUMN embedding VECTOR(1536);

CREATE INDEX idx_interviews_embedding_hnsw
    ON interviews
    USING hnsw (embedding vector_cosine_ops);
```

엔티티에서는 Hibernate Vector 타입으로 매핑합니다.

```java
@JdbcTypeCode(SqlTypes.VECTOR)
@Array(length = 1536)
@Column(name = "embedding", columnDefinition = "vector(1536)")
private float[] embedding;
```

임베딩 차원 `1536`은 SQL, JPA `@Array`, 실제 임베딩 모델 출력에서 모두 동일해야 합니다.

## 변경 사항 검증

### 일반 검증

DB 컨테이너가 실행 중인 상태에서 다음 명령을 실행합니다.

```bash
./gradlew test
```

애플리케이션을 직접 실행하면 Flyway 마이그레이션과 Hibernate 스키마 검증을 함께 확인할 수 있습니다.

```bash
./gradlew bootRun
```

### 깨끗한 DB에서 최초 구축 검증

로컬 데이터를 삭제해도 되는 경우에만 다음 명령을 사용합니다.

```bash
docker compose down -v
docker compose up -d --wait
./gradlew test
```

검증할 내용은 다음과 같습니다.

- 모든 Flyway 마이그레이션의 `success` 값이 `true`인지
- 애플리케이션이 checksum 오류 없이 실행되는지
- Hibernate `ddl-auto: validate`가 통과하는지
- 새 컬럼과 테이블이 DBeaver에서 확인되는지
- 외래키, unique, check 제약조건이 의도대로 생성됐는지
- 필요한 일반 인덱스와 vector 인덱스가 생성됐는지

## 주의 사항

- Flyway가 트랜잭션을 관리하므로 마이그레이션 SQL에 `BEGIN`과 `COMMIT`을 직접 작성하지 않습니다.
- 테이블과 컬럼 이름은 프로젝트의 `snake_case` 규칙을 사용합니다.
- 외래키 컬럼은 PostgreSQL이 인덱스를 자동 생성하지 않으므로 조회 패턴에 필요한 인덱스를 명시합니다.
- enum을 문자열로 저장할 때는 SQL `CHECK` 목록과 Java enum 값을 함께 수정합니다.
- `created_at`, `updated_at`은 공통 엔티티 매핑과 SQL 기본값·트리거를 함께 확인합니다.
- 로컬에서 checksum 오류가 발생해도 공유된 마이그레이션 파일을 다시 수정하거나 무조건 `repair`하지 않습니다.
  먼저 팀의 최신 마이그레이션 파일과 자신의 DB 적용 이력을 비교합니다.


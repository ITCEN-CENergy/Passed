"""psycopg 기반 DB 연결·Flyway 계약 검증·파이프라인 캐시 초기화."""

from __future__ import annotations

from contextlib import contextmanager
import logging
from pathlib import Path
from typing import Iterator

import psycopg
from psycopg import Connection

from .config import get_settings

logger = logging.getLogger(__name__)

# SQL은 Python 패키지 밖의 job-posting/schema 디렉터리에서 관리한다.
SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"

REQUIRED_COLUMNS = {
    "job_postings": {
        "id", "title", "company_id", "job_role_id", "start_ymd", "end_ymd",
        "headcount", "career_type", "hire_type", "region", "edu_level",
        "position_detail", "main_duty", "qualification", "preference",
        "disqualify_reason", "process",
    },
    "job_posting_chunks": {
        "id", "job_posting_id", "source_type", "chunk_index", "chunk_content",
        "embedding", "embedding_model", "embedding_status",
        "embedding_updated_at", "content_hash", "created_at", "updated_at",
    },
    "companies": {"id"},
    "job_roles": {"id"},
}


class DatabaseContractError(RuntimeError):
    """현재 DB가 파이프라인이 요구하는 Flyway 계약과 다를 때 발생한다."""


def connect() -> Connection:
    """`.env`의 DATABASE_URL을 사용해 PostgreSQL 연결을 반환한다."""
    # 비밀번호를 소스코드에 직접 저장하지 않고 환경설정에서만 가져온다.
    settings = get_settings()
    return psycopg.connect(
        settings.database_url,
        connect_timeout=settings.database_connect_timeout_seconds,
    )


@contextmanager
def connection() -> Iterator[Connection]:
    """성공 시 commit, 예외 시 rollback하고 연결을 닫는 컨텍스트."""
    conn = connect()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_schema(conn: Connection) -> None:
    """Flyway 스키마를 검증하고 Python 전용 추출 캐시만 준비한다."""
    # 백엔드 소유 테이블은 Python이 생성·변경하지 않는다.
    validate_database_contract(conn)
    schema_files = [SCHEMA_DIR / "02_extraction_meta.sql"]
    with conn.cursor() as cur:
        for path in schema_files:
            logger.info("DB 스키마 적용: %s", path.name)
            cur.execute(path.read_text(encoding="utf-8"))
    conn.commit()
    logger.info("파이프라인 전용 DB 스키마 초기화 완료")


def validate_database_contract(conn: Connection) -> None:
    """5433의 Flyway V3 청크 스키마와 필수 컬럼·벡터 차원을 검증한다."""
    problems: list[str] = []
    with conn.cursor() as cur:
        cur.execute(
            "SELECT table_name, column_name FROM information_schema.columns "
            "WHERE table_schema = 'public' AND table_name = ANY(%s)",
            (list(REQUIRED_COLUMNS),),
        )
        actual: dict[str, set[str]] = {}
        for table_name, column_name in cur.fetchall():
            actual.setdefault(table_name, set()).add(column_name)
        for table_name, required in REQUIRED_COLUMNS.items():
            missing = sorted(required - actual.get(table_name, set()))
            if missing:
                problems.append(f"{table_name} 누락 컬럼={missing}")

        cur.execute(
            "SELECT format_type(a.atttypid, a.atttypmod) "
            "FROM pg_attribute a "
            "JOIN pg_class c ON c.oid = a.attrelid "
            "JOIN pg_namespace n ON n.oid = c.relnamespace "
            "WHERE n.nspname='public' AND c.relname='job_posting_chunks' "
            "AND a.attname='embedding'"
        )
        row = cur.fetchone()
        if row is None or row[0] != "vector(1536)":
            problems.append(
                "job_posting_chunks.embedding은 vector(1536)이어야 합니다"
            )

    if problems:
        raise DatabaseContractError("; ".join(problems))
    logger.info("DB 계약 검증 완료: Flyway V3 job posting schema 호환")

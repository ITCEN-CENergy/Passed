"""psycopg 기반 DB 연결·스키마 초기화 유틸.

범용 Connection/커서 헬퍼와 `job_posting_chunks` 생성 스크립트 실행을 담당한다.
`job_postings`, `companies`, `job_roles`는 기존(Spring) DB에 이미 존재한다고 가정한다.
"""

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


def connect() -> Connection:
    """`.env`의 DATABASE_URL을 사용해 PostgreSQL 연결을 반환한다."""
    # 비밀번호를 소스코드에 직접 저장하지 않고 환경설정에서만 가져온다.
    return psycopg.connect(get_settings().database_url)


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
    """pgvector 확장과 청크·추출 캐시 테이블을 순서대로 준비한다."""
    # 파일 순서가 의존 순서다: 확장 -> 청크 테이블 -> 추출 캐시 테이블.
    schema_files = [
        SCHEMA_DIR / "00_extensions.sql",
        SCHEMA_DIR / "01_job_posting_chunks.sql",
        SCHEMA_DIR / "02_extraction_meta.sql",
    ]
    with conn.cursor() as cur:
        for path in schema_files:
            logger.info("DB 스키마 적용: %s", path.name)
            cur.execute(path.read_text(encoding="utf-8"))
    conn.commit()
    logger.info("DB 스키마 초기화 완료")

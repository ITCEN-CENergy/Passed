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

# Python 패키지는 job_posting_pipeline/, SQL 스키마는 작업 디렉터리의 schema/에 둔다.
SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"


def connect() -> Connection:
    """설정에서 읽은 DATABASE_URL로 psycopg 연결을 반환한다."""
    return psycopg.connect("postgresql://passed:test1234@localhost:5432/postgres")

@contextmanager
def connection() -> Iterator[Connection]:
    """트랜잭션 자동 commit/rollback 컨텍스트. 기본 autocommit=False."""
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
    """`schema/`의 SQL 파일을 순서대로 실행한다.

    - `00_extensions.sql`  : pgvector 확장
    - `01_job_posting_chunks.sql` : 청크 테이블·인덱스·트리거
    - `02_extraction_meta.sql` : LLM 추출 입력 해시 캐시 테이블(권장)
    """
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

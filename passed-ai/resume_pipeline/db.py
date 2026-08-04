"""resume_pipeline용 PostgreSQL 연결."""

from __future__ import annotations

from contextlib import contextmanager
import os
from typing import Iterator

import psycopg
from psycopg import Connection
from psycopg.rows import dict_row


def connect() -> Connection:
    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        raise RuntimeError("DATABASE_URL 환경변수가 필요합니다.")
    return psycopg.connect(database_url, row_factory=dict_row)


@contextmanager
def connection() -> Iterator[Connection]:
    conn = connect()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

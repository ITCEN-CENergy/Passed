"""resume_pipeline용 PostgreSQL 연결."""

from __future__ import annotations

from contextlib import contextmanager
import os
from typing import Iterator

import psycopg
from pgvector.psycopg import register_vector
from psycopg import Connection
from psycopg.rows import dict_row
from dotenv import load_dotenv
load_dotenv()   # os.environ 읽기 전에 호출되어야 함


EMBEDDING_TABLES = ("resume_chunks", "cover_letter_chunks")
REQUIRED_EMBEDDING_COLUMNS = {
    "id",
    "chunk_content",
    "embedding",
    "content_hash",
    "embedding_model",
    "embedding_status",
    "embedding_updated_at",
}


class EmbeddingSchemaError(RuntimeError):
    """현재 DB가 임베딩 작업자가 요구하는 스키마와 다를 때 발생한다."""


def connect() -> Connection:
    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        raise RuntimeError("DATABASE_URL 환경변수가 필요합니다.")
    conn = psycopg.connect(database_url, row_factory=dict_row)

    # Q. psycopg가 list[float]를 알아서 vector 컬럼에 넣지 못하나요?
    # A. PostgreSQL 기본 타입에는 vector가 없습니다. pgvector 어댑터를 연결마다
    #    등록해야 Python 벡터를 안전하게 직렬화하고 vector(1536)로 저장할 수 있습니다.
    register_vector(conn)
    return conn


def validate_embedding_schema(conn: Connection) -> None:
    """두 청크 테이블의 필수 컬럼과 vector 차원을 읽기 전용으로 검증한다."""
    actual_columns: dict[str, set[str]] = {}
    with conn.cursor() as cur:
        cur.execute(
            "SELECT table_name, column_name FROM information_schema.columns "
            "WHERE table_schema = 'public' AND table_name = ANY(%s)",
            (list(EMBEDDING_TABLES),),
        )
        for row in cur.fetchall():
            table_name = row["table_name"] if isinstance(row, dict) else row[0]
            column_name = row["column_name"] if isinstance(row, dict) else row[1]
            actual_columns.setdefault(str(table_name), set()).add(str(column_name))

        problems: list[str] = []
        for table in EMBEDDING_TABLES:
            missing = sorted(REQUIRED_EMBEDDING_COLUMNS - actual_columns.get(table, set()))
            if missing:
                problems.append(f"{table} 누락 컬럼={missing}")

        cur.execute(
            "SELECT c.relname AS table_name, format_type(a.atttypid, a.atttypmod) AS type_name "
            "FROM pg_attribute a "
            "JOIN pg_class c ON c.oid = a.attrelid "
            "JOIN pg_namespace n ON n.oid = c.relnamespace "
            "WHERE n.nspname = 'public' AND c.relname = ANY(%s) "
            "AND a.attname = 'embedding'",
            (list(EMBEDDING_TABLES),),
        )
        vector_types = {
            str(row["table_name"] if isinstance(row, dict) else row[0]):
            str(row["type_name"] if isinstance(row, dict) else row[1])
            for row in cur.fetchall()
        }

    for table in EMBEDDING_TABLES:
        if vector_types.get(table) != "vector(1536)":
            problems.append(
                f"{table}.embedding 타입은 vector(1536)이어야 합니다: "
                f"actual={vector_types.get(table)}"
            )

    if problems:
        raise EmbeddingSchemaError("; ".join(problems))


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

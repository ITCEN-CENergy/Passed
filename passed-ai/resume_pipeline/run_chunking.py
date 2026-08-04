"""사용자 문서 청킹 CLI.

사용 예:
    python -m resume_pipeline.run_chunking --user-id 19
    python -m resume_pipeline.run_chunking --email test@passed.dev
"""

from __future__ import annotations

import argparse
import logging

import psycopg

from .db import connection
from .pipeline import MissingResumeError, run_chunking_for_user


class UserNotFoundError(Exception):
    """이메일로 사용자를 찾지 못한 경우."""


def resolve_user_id(conn: psycopg.Connection, email: str) -> int:
    """이메일로 user_id를 조회한다. 팀원마다 id가 다르므로 이메일 조회를 권장."""
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE email = %s", (email,))
        row = cur.fetchone()

    if row is None:
        raise UserNotFoundError(
            f"해당 이메일의 사용자를 찾을 수 없습니다: {email}. "
            "시드 마이그레이션이 적용됐는지, DATABASE_URL이 맞는지 확인하세요."
        )

    # row_factory 설정에 따라 dict 또는 tuple로 올 수 있어 둘 다 처리한다.
    return row["id"] if isinstance(row, dict) else row[0]


def main() -> None:
    parser = argparse.ArgumentParser(description="이력서·자기소개서 청크 동기화")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--user-id", type=int, help="대상 사용자 ID")
    target.add_argument("--email", type=str, help="대상 사용자 이메일 (ID를 모를 때)")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    try:
        with connection() as conn:
            user_id = args.user_id if args.user_id else resolve_user_id(conn, args.email)
            result = run_chunking_for_user(conn, user_id)
    except UserNotFoundError as exc:
        logging.error("청크 동기화 실패: %s", exc)
        raise SystemExit(2) from exc
    except MissingResumeError as exc:
        logging.error("청크 동기화 실패: %s", exc)
        raise SystemExit(2) from exc

    logging.info("청크 동기화 완료 user_id=%s result=%s", user_id, result)


if __name__ == "__main__":
    main()
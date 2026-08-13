"""CLI들이 공통으로 사용하는 사용자 식별 로직."""

from __future__ import annotations

from typing import Any


class UserNotFoundError(ValueError):
    """이메일에 해당하는 사용자를 찾지 못했을 때 발생한다."""


def resolve_user_id(conn: Any, email: str) -> int:
    """팀원마다 달라질 수 있는 ID 대신 고정 이메일로 user_id를 찾는다."""
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE email = %s", (email,))
        row = cur.fetchone()

    if row is None:
        raise UserNotFoundError(
            f"해당 이메일의 사용자를 찾을 수 없습니다: {email}. "
            "시드 마이그레이션과 DATABASE_URL을 확인하세요."
        )

    return int(row["id"] if isinstance(row, dict) else row[0])
 
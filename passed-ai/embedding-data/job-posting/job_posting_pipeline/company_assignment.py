"""company_id 결정적 임의 배정(계획서 5절).

Python 내장 hash()는 프로세스마다 결과가 달라질 수 있어 사용하지 않고
SHA-256(job_posting_id + fixed_seed) % 160 으로 0~159 중 하나를 선택한다.
"""

from __future__ import annotations

import hashlib

from .config import get_settings


# ---------------------------------------------------------------------------
# 결정적 회사 배정
# ---------------------------------------------------------------------------
def assign_company_id(job_posting_id: int) -> int:
    """company_id 가 주어지지 않은 공고의 회사를 결정적으로 배정한다.

    같은 job_posting_id + 같은 시드면 항상 같은 결과를 보장한다.
    """
    settings = get_settings()
    # 공고 ID와 고정 시드를 결합하면 반복 적재해도 같은 회사가 선택된다.
    payload = f"{job_posting_id}{settings.company_assignment_seed}".encode("utf-8")
    digest = hashlib.sha256(payload).digest()
    as_int = int.from_bytes(digest, byteorder="big")
    span = settings.company_id_max - settings.company_id_min + 1
    return settings.company_id_min + (as_int % span)


# ---------------------------------------------------------------------------
# 외래키 사전 검증
# ---------------------------------------------------------------------------
def check_company_id_range(
    conn, min_id: int | None = None, max_id: int | None = None
) -> list[int]:
    """companies.id 에 min_id~max_id 범위의 값이 모두 존재하는지 검증.

    누락된 ID 목록을 반환한다. 하나라도 없으면 적재를 시작하지 않기 위함.
    """
    settings = get_settings()
    lo = min_id if min_id is not None else settings.company_id_min
    hi = max_id if max_id is not None else settings.company_id_max
    # 개발용 기준정보 전체 범위를 점검할 때 사용하는 함수다.
    expected = set(range(lo, hi + 1))
    # 실제 입력에서 사용한 ID만 조회해 불필요한 전체 범위 검사를 피한다.
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM companies WHERE id BETWEEN %s AND %s", (lo, hi))
        present = {r[0] for r in cur.fetchall()}
    return sorted(expected - present)


def check_company_ids(conn, company_ids: set[int]) -> list[int]:
    """CSV가 실제로 참조하는 company ID의 존재 여부만 검증한다."""
    if not company_ids:
        return []
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM companies WHERE id = ANY(%s)",
            (sorted(company_ids),),
        )
        present = {row[0] for row in cur.fetchall()}
    return sorted(company_ids - present)

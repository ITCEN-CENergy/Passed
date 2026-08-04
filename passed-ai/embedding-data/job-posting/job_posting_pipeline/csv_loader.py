"""CSV 적재 -> job_postings UPSERT(계획서 2·5·14 2단계).

기존 FastAPI 서버와 분리된 일괄 작업 모듈이다.
"""

from __future__ import annotations

import csv
from collections import Counter, defaultdict
import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from psycopg import Connection

from .normalize import (
    normalize_career,
    normalize_edu_level,
    normalize_hire_type,
    normalize_region,
)

logger = logging.getLogger(__name__)

_DATE_RE = re.compile(r"^\d{8}$")
_POSTINGS_PER_ROLE = 10


# CSV 한 행의 형식 또는 참조 데이터가 적재 조건을 만족하지 못한 경우 사용한다.
class CSVRowError(ValueError):
    """CSV 행 검증 실패."""


@dataclass
class LoadResult:
    loaded: int = 0
    failed: int = 0
    failures: list[tuple[int, str]] = field(default_factory=list)
    job_posting_ids: list[int] = field(default_factory=list)


# ---------------------------------------------------------------------------
# 파일 읽기와 값 변환
# ---------------------------------------------------------------------------
def read_csv_rows(csv_path: str | Path) -> tuple[list[dict[str, str]], str]:
    """CSV를 UTF-8 우선으로 읽고, 실패하면 CP949로 다시 읽는다.

    반환값은 `(행 목록, 사용한 인코딩)`이다. UTF-8 BOM은 `utf-8-sig`가
    제거하므로 `job_posting_id` 헤더에 BOM이 남지 않는다.
    """
    path = Path(csv_path)
    last_error: UnicodeDecodeError | None = None
    for encoding in ("utf-8-sig", "cp949"):
        try:
            with path.open("r", encoding=encoding, newline="") as file:
                return list(csv.DictReader(file)), encoding
        except UnicodeDecodeError as exc:
            last_error = exc

    # 현재 지원 인코딩 모두 실패한 경우 최초 원인을 보존해 명확하게 실패한다.
    assert last_error is not None
    raise last_error


def restore_missing_job_posting_ids(
    rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    """직무별 10건 fixture에 한해 누락된 공고 ID를 결정적으로 복원한다."""
    if not rows:
        return rows
    values = [_to_none(row.get("job_posting_id")) for row in rows]
    if all(value is not None for value in values):
        return rows
    if any(value is not None for value in values):
        raise CSVRowError("job_posting_id가 일부 행에만 존재합니다.")
    try:
        role_ids = [int(_require(row.get("job_role_id"), "job_role_id")) for row in rows]
    except (CSVRowError, ValueError) as exc:
        raise CSVRowError("job_posting_id 자동 복원 중 job_role_id 형식 오류") from exc
    counts = Counter(role_ids)
    invalid = sorted(k for k, v in counts.items() if k <= 0 or v != _POSTINGS_PER_ROLE)
    if invalid:
        raise CSVRowError(
            "job_posting_id가 없고 직무별 10건 fixture 규칙도 맞지 않습니다: "
            f"{invalid[:20]}{'...' if len(invalid) > 20 else ''}"
        )
    occurrences: defaultdict[int, int] = defaultdict(int)
    restored: list[dict[str, str]] = []
    for row, role_id in zip(rows, role_ids):
        occurrences[role_id] += 1
        copied = dict(row)
        copied["job_posting_id"] = str(
            (role_id - 1) * _POSTINGS_PER_ROLE + occurrences[role_id]
        )
        restored.append(copied)
    return restored


def _to_none(value: str | None) -> str | None:
    """문자열 'NULL'을 실제 None 으로 변환. 빈 문자열과 Excel 오류값도 None."""
    if value is None:
        return None
    v = value.strip()
    if v == "" or v.upper() == "NULL" or v.upper() in {
        "#NAME?", "#REF!", "#VALUE!", "#N/A", "#DIV/0!", "#NULL!", "#NUM?"
    }:
        return None
    return v


def _to_int_nullable(value: str | None) -> int | None:
    v = _to_none(value)
    if v is None:
        return None
    try:
        return int(float(v))  # "4.0" 같은 엑셀 표기 허용
    except (TypeError, ValueError) as exc:
        raise CSVRowError(f"정수 변환 실패: {value!r}") from exc


def _require(value: str | None, field_name: str) -> str:
    if _to_none(value) is None:
        raise CSVRowError(f"필수값 누락: {field_name}")
    return value.strip()  # type: ignore[union-attr]


def _validate_date(value: str | None, field_name: str) -> str | None:
    v = _to_none(value)
    if v is None:
        return None
    if not _DATE_RE.match(v):
        raise CSVRowError(f"{field_name} 형식 오류(YYYYMMDD): {v!r}")
    return v


def parse_row(raw: dict[str, str]) -> dict:
    """CSV 행을 검증·정규화된 job_postings 레코드로 변환.

    검증 실패 시 CSVRowError 를 발생시킨다.
    """
    # BOM/동일 컬럼명 호환: 키 앞의 \ufeff 제거
    row = {k.lstrip("\ufeff").strip(): v for k, v in raw.items()}

    # PK/FK와 날짜처럼 DB 무결성에 직접 영향을 주는 값부터 검증한다.
    job_posting_id = _require(row.get("job_posting_id"), "job_posting_id")
    job_posting_id = int(job_posting_id)

    title = _require(row.get("title"), "title").strip()

    company_id = int(_require(row.get("company_id"), "company_id"))

    job_role_id = _require(row.get("job_role_id"), "job_role_id")
    job_role_id = int(job_role_id)

    start_ymd = _validate_date(row.get("start_ymd"), "start_ymd")
    end_ymd = _validate_date(row.get("end_ymd"), "end_ymd")
    if start_ymd and end_ymd and start_ymd > end_ymd:
        raise CSVRowError(
            f"공고 기간 오류: start_ymd={start_ymd} end_ymd={end_ymd}"
        )

    headcount = _to_int_nullable(row.get("headcount"))
    if headcount is not None and headcount <= 0:
        raise CSVRowError(f"headcount는 1 이상이어야 합니다: {headcount}")

    career_type = normalize_career(_to_none(row.get("career_type")))
    # 새 CSV는 DB 컬럼명과 동일하다. 이전 *_lst fixture도 읽기 호환한다.
    hire_type = normalize_hire_type(
        _to_none(row.get("hire_type") or row.get("hire_type_lst"))
    )
    region = normalize_region(
        _to_none(row.get("region") or row.get("region_lst"))
    )
    edu_level = normalize_edu_level(
        _to_none(row.get("edu_level") or row.get("edu_level_lst"))
    )

    return {
        "id": job_posting_id,
        "title": title,
        "company_id": company_id,
        "job_role_id": job_role_id,
        "start_ymd": start_ymd,
        "end_ymd": end_ymd,
        "headcount": headcount,
        "career_type": career_type,
        "hire_type": hire_type,
        "region": region,
        "edu_level": edu_level,
        # 원문 보존(화면 표시용)
        "position_detail": _to_none(row.get("position_detail")),
        "main_duty": _to_none(row.get("main_duty")),
        "qualification": _to_none(row.get("qualification")),
        "preference": _to_none(row.get("preference")),
        "disqualify_reason": _to_none(row.get("disqualify_reason")),
        "process": _to_none(row.get("process")),
    }


# ---------------------------------------------------------------------------
# job_postings 저장 SQL
# ---------------------------------------------------------------------------
_UPSERT_SQL = """
INSERT INTO job_postings (
    id, title, company_id, job_role_id, start_ymd, end_ymd, headcount,
    career_type, hire_type, region, edu_level,
    position_detail, main_duty, qualification, preference,
    disqualify_reason, process
) OVERRIDING SYSTEM VALUE VALUES (
    %(id)s, %(title)s, %(company_id)s, %(job_role_id)s, %(start_ymd)s, %(end_ymd)s,
    %(headcount)s, %(career_type)s, %(hire_type)s, %(region)s, %(edu_level)s,
    %(position_detail)s, %(main_duty)s, %(qualification)s, %(preference)s,
    %(disqualify_reason)s, %(process)s
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    company_id = EXCLUDED.company_id,
    job_role_id = EXCLUDED.job_role_id,
    start_ymd = EXCLUDED.start_ymd,
    end_ymd = EXCLUDED.end_ymd,
    headcount = EXCLUDED.headcount,
    career_type = EXCLUDED.career_type,
    hire_type = EXCLUDED.hire_type,
    region = EXCLUDED.region,
    edu_level = EXCLUDED.edu_level,
    position_detail = EXCLUDED.position_detail,
    main_duty = EXCLUDED.main_duty,
    qualification = EXCLUDED.qualification,
    preference = EXCLUDED.preference,
    disqualify_reason = EXCLUDED.disqualify_reason,
    process = EXCLUDED.process
"""


def _upsert_posting(conn: Connection, record: dict) -> None:
    with conn.cursor() as cur:
        cur.execute(_UPSERT_SQL, record)


def _fix_sequence(conn: Connection) -> None:
    """명시적 ID 삽입 후 PK 시퀀스를 MAX(id) 이후로 보정.

    job_postings.id 가 GENERATED ALWAYS AS IDENTITY 기반이라고 가정한다.
    """
    # CSV가 identity 값을 명시하므로 다음 자동 INSERT가 충돌하지 않게 맞춘다.
    with conn.cursor() as cur:
        cur.execute(
            "SELECT setval("
            "pg_get_serial_sequence('job_postings','id'), "
            "COALESCE((SELECT MAX(id) FROM job_postings), 0) + 1, false)"
        )


def _check_job_roles_exist(conn: Connection, job_role_ids: Iterable[int]) -> list[int]:
    ids = set(job_role_ids)
    if not ids:
        return []
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM job_roles WHERE id = ANY(%s)",
            (sorted(ids),),
        )
        present = {r[0] for r in cur.fetchall()}
    return sorted(ids - present)


def check_company_ids(conn: Connection, company_ids: set[int]) -> list[int]:
    """CSV가 참조하는 company ID가 모두 존재하는지 검증한다."""
    if not company_ids:
        return []
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM companies WHERE id = ANY(%s)",
            (sorted(company_ids),),
        )
        present = {row[0] for row in cur.fetchall()}
    return sorted(company_ids - present)


# ---------------------------------------------------------------------------
# CSV 전체 적재 오케스트레이션
# ---------------------------------------------------------------------------
def load_csv(conn: Connection, csv_path: str | Path) -> LoadResult:
    """하나의 CSV 파일을 읽어 job_postings 에 UPSERT 한다.

    행 단위로 실패해도 다음 행을 계속 처리하고, 실패 내역을 결과에 남긴다.
    적재 전에 CSV가 실제 참조하는 companies.id와 job_roles.id를 검증한다.
    """
    result = LoadResult()
    csv_path = Path(csv_path)

    # 1차 패스: 인코딩 감지 후 파싱(검증·정규화)
    parsed: list[dict] = []
    # normalize_text 로 원문 정리 과정은 화면용이 아니므로 파싱 단계에서는 원문 보존.
    rows, encoding = read_csv_rows(csv_path)
    logger.info(
        "CSV 읽기 완료: file=%s encoding=%s rows=%d",
        csv_path.name,
        encoding,
        len(rows),
    )
    had_ids = bool(rows) and all(
        _to_none(row.get("job_posting_id")) is not None for row in rows
    )
    rows = restore_missing_job_posting_ids(rows)
    if rows and not had_ids:
        logger.warning(
            "job_posting_id 자동 복원: file=%s rows=%d first_id=%s last_id=%s",
            csv_path.name, len(rows),
            rows[0]["job_posting_id"], rows[-1]["job_posting_id"],
        )
    for line_no, raw in enumerate(rows, start=2):  # 헤더 1행
        try:
            parsed.append(parse_row(raw))
        except CSVRowError as exc:
            result.failed += 1
            result.failures.append((line_no, str(exc)))
            logger.warning("CSV %s 행 %d 검증 실패: %s", csv_path.name, line_no, exc)

    # 참조 데이터 사전 검증: INSERT 전에 중단해 트랜잭션 연쇄 실패를 방지한다.
    missing_companies = check_company_ids(
        conn, {record["company_id"] for record in parsed}
    )
    if missing_companies:
        raise CSVRowError(
            f"CSV에서 참조하는 companies.id 누락: {missing_companies[:20]}"
            f"{'...' if len(missing_companies) > 20 else ''} "
            f"(총 {len(missing_companies)}개)"
        )

    missing_roles = _check_job_roles_exist(
        conn, {r["job_role_id"] for r in parsed}
    )
    if missing_roles:
        raise CSVRowError(
            f"CSV에서 참조하는 job_roles.id 누락: {missing_roles[:20]}"
            f"{'...' if len(missing_roles) > 20 else ''} "
            f"(총 {len(missing_roles)}개)"
        )

    # 2차 패스: UPSERT
    for rec in parsed:
        try:
            # 바깥 적재 트랜잭션 안에서 행별 SAVEPOINT를 사용한다.
            # 한 행의 DB 오류가 발생해도 다음 행은 정상 처리할 수 있다.
            with conn.transaction():
                _upsert_posting(conn, rec)
            result.loaded += 1
            result.job_posting_ids.append(rec["id"])
        except Exception as exc:  # noqa: BLE001 - 행 단위 격리
            result.failed += 1
            result.failures.append((rec["id"], str(exc)))
            logger.warning("job_posting_id=%s 적재 실패: %s", rec["id"], exc)

    # 시퀀스 보정
    try:
        _fix_sequence(conn)
    except Exception as exc:  # noqa: BLE001
        logger.warning("시퀀스 보정 실패(무시 가능): %s", exc)

    logger.info(
        "CSV 적재 완료 %s: loaded=%d failed=%d",
        csv_path.name,
        result.loaded,
        result.failed,
    )
    return result


def fetch_posting(conn: Connection, job_posting_id: int) -> dict | None:
    """job_postings 원문을 청크 생성 입력으로 가져온다."""
    # 화면 표시용 원문을 그대로 가져오고 정규화는 chunker에서만 수행한다.
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, title, position_detail, main_duty, qualification, "
            "preference, disqualify_reason, process "
            "FROM job_postings WHERE id = %s",
            (job_posting_id,),
        )
        row = cur.fetchone()
    if row is None:
        return None
    cols = ["id", "title", "position_detail", "main_duty", "qualification",
            "preference", "disqualify_reason", "process"]
    return dict(zip(cols, row))

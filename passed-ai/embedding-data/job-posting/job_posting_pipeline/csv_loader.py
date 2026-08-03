"""CSV 적재 -> job_postings UPSERT(계획서 2·5·14 2단계).

기존 FastAPI 서버와 분리된 일괄 작업 모듈이다.
"""

from __future__ import annotations

import csv
import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from psycopg import Connection

from .company_assignment import assign_company_id, check_company_ids
from .normalize import (
    normalize_career,
    normalize_edu_level,
    normalize_hire_type,
    normalize_region,
)

logger = logging.getLogger(__name__)

_DATE_RE = re.compile(r"^\d{8}$")


class CSVRowError(ValueError):
    """CSV 행 검증 실패."""


@dataclass
class LoadResult:
    loaded: int = 0
    failed: int = 0
    failures: list[tuple[int, str]] = field(default_factory=list)
    job_posting_ids: list[int] = field(default_factory=list)


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

    job_posting_id = _require(row.get("job_posting_id"), "job_posting_id")
    job_posting_id = int(job_posting_id)

    title = _require(row.get("title"), "title").strip()

    company_id = _to_int_nullable(row.get("company_id"))
    if company_id is None:
        company_id = assign_company_id(job_posting_id)

    job_role_id = _require(row.get("job_role_id"), "job_role_id")
    job_role_id = int(job_role_id)

    start_ymd = _validate_date(row.get("start_ymd"), "start_ymd")
    end_ymd = _validate_date(row.get("end_ymd"), "end_ymd")

    headcount = _to_int_nullable(row.get("headcount"))

    career_type = normalize_career(_to_none(row.get("career_type")))
    hire_type = normalize_hire_type(_to_none(row.get("hire_type_lst")))
    region = normalize_region(_to_none(row.get("region_lst")))
    edu_level = normalize_edu_level(_to_none(row.get("edu_level_lst")))

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


_UPSERT_SQL = """
INSERT INTO job_postings (
    id, title, company_id, job_role_id, start_ymd, end_ymd, headcount,
    career_type, hire_type, region, edu_level,
    position_detail, main_duty, qualification, preference,
    disqualify_reason, process
) VALUES (
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

    job_postings.id 가 IDENTITY(DEFAULT) 기반이라고 가정한다.
    """
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
    # normalize_text 는 청크 생성 단계에서 적용한다.
    _ = normalize_text  # 정규화는 청커에서 수행
    cols = ["id", "title", "position_detail", "main_duty", "qualification",
            "preference", "disqualify_reason", "process"]
    return dict(zip(cols, row))

"""이력서 원본 행을 스킬 추출용 텍스트로 조립한다."""

from __future__ import annotations

from collections.abc import Callable, Iterable, Mapping
from typing import Any

from .models import ResumeChunk, ResumeSourceType
from .text_utils import content_hash, format_date, format_period, normalize_text


def _line(label: str, value: Any) -> str | None:
    text = normalize_text(value)
    return f"{label}: {text}" if text else None


def _period_line(
    row: Mapping[str, Any],
    *,
    ongoing_key: str | None = None,
) -> str | None:
    ongoing = bool(row.get(ongoing_key)) if ongoing_key else False
    period = format_period(row.get("start_date"), row.get("end_date"), ongoing=ongoing)
    return f"기간: {period}" if period else None


def _join(lines: Iterable[str | None]) -> str:
    return "\n".join(line for line in lines if line)


def _education(row: Mapping[str, Any]) -> str:
    period = format_period(row.get("admission_date"), row.get("graduation_date"))
    gpa = ""
    if row.get("gpa") is not None and row.get("max_gpa") is not None:
        gpa = f"{row['gpa']} / {row['max_gpa']}"
    elif row.get("gpa") is not None:
        gpa = str(row["gpa"])
    transfer = row.get("is_transfer")
    transfer_text = "편입" if transfer is True else "일반 입학" if transfer is False else ""
    return _join(
        [
            _line("학교 유형", row.get("school_type")),
            _line("학교", row.get("school_name")),
            f"기간: {period}" if period else None,
            _line("상태", row.get("status")),
            _line("입학 구분", transfer_text),
            _line("전공", row.get("major_name")),
            _line("학점", gpa),
            _line("기타 전공", row.get("other_majors")),
        ]
    )


def _experience(row: Mapping[str, Any]) -> str:
    return _join(
        [
            _line("회사", row.get("company_name")),
            _line("부서", row.get("department_name")),
            _period_line(row, ongoing_key="is_working"),
            _line("직책", row.get("position")),
            _line("담당 업무", row.get("responsibilities")),
            _line("경력 설명", row.get("career_desc")),
        ]
    )


def _activity(row: Mapping[str, Any]) -> str:
    return _join(
        [
            _line("활동 유형", row.get("activity_type")),
            _line("기관", row.get("organization")),
            _period_line(row),
            _line("활동 내용", row.get("description")),
        ]
    )


def _training(row: Mapping[str, Any]) -> str:
    return _join(
        [
            _line("교육명", row.get("name")),
            _line("교육 기관", row.get("institution")),
            _period_line(row),
            _line("교육 내용", row.get("description")),
        ]
    )


def _certification(row: Mapping[str, Any]) -> str:
    acquired = format_date(row.get("acquisition_date"))
    return _join(
        [
            _line("자격증", row.get("name")),
            _line("발급 기관", row.get("issuer")),
            _line("취득일", acquired),
        ]
    )


def _award(row: Mapping[str, Any]) -> str:
    awarded = format_date(row.get("award_date"))
    return _join(
        [
            _line("수상명", row.get("name")),
            _line("수여 기관", row.get("issuer")),
            _line("수상일", awarded),
            _line("수상 내용", row.get("description")),
        ]
    )


def _overseas_experience(row: Mapping[str, Any]) -> str:
    return _join(
        [
            _line("국가", row.get("country_name")),
            _period_line(row),
            _line("해외 경험", row.get("description")),
        ]
    )


def _language(row: Mapping[str, Any]) -> str:
    return _join(
        [
            _line("언어", row.get("language_name")),
            _line("수준", row.get("proficiency_level")),
        ]
    )


_BUILDERS: dict[ResumeSourceType, Callable[[Mapping[str, Any]], str]] = {
    ResumeSourceType.EDUCATION: _education,
    ResumeSourceType.EXPERIENCE: _experience,
    ResumeSourceType.ACTIVITY: _activity,
    ResumeSourceType.TRAINING: _training,
    ResumeSourceType.CERTIFICATION: _certification,
    ResumeSourceType.AWARD: _award,
    ResumeSourceType.OVERSEAS_EXPERIENCE: _overseas_experience,
    ResumeSourceType.LANGUAGE: _language,
}


def build_resume_text(
    source_type: ResumeSourceType,
    row: Mapping[str, Any],
) -> str:
    """한 이력서 하위 행을 항상 같은 순서의 텍스트로 조립한다."""
    return _BUILDERS[source_type](row)


def build_resume_chunks(
    resume_id: int,
    source_type: ResumeSourceType,
    rows: Iterable[Mapping[str, Any]],
) -> list[ResumeChunk]:
    chunks: list[ResumeChunk] = []
    for row in sorted(rows, key=lambda item: int(item["id"])):
        text = build_resume_text(source_type, row)
        if not text:
            continue
        chunks.append(
            ResumeChunk(
                resume_id=resume_id,
                source_type=source_type,
                source_id=int(row["id"]),
                chunk_index=0,
                chunk_content=text,
                content_hash=content_hash(text),
            )
        )
    return chunks

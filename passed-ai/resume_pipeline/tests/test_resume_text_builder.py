from __future__ import annotations

from datetime import date

import pytest

from resume_pipeline.models import ResumeSourceType
from resume_pipeline.resume_text_builder import build_resume_chunks, build_resume_text


def test_experience_excludes_nulls_and_formats_ongoing_period():
    text = build_resume_text(
        ResumeSourceType.EXPERIENCE,
        {
            "company_name": "카카오",
            "department_name": None,
            "start_date": date(2024, 1, 2),
            "end_date": None,
            "is_working": True,
            "position": "백엔드 개발자",
            "responsibilities": " API 개발 ",
            "career_desc": None,
        },
    )

    assert "회사: 카카오" in text
    assert "기간: 2024-01-02 ~ 재직 중" in text
    assert "담당 업무: API 개발" in text
    assert "부서:" not in text
    assert "None" not in text


def test_education_combines_gpa_and_period():
    text = build_resume_text(
        ResumeSourceType.EDUCATION,
        {
            "school_name": "한국대학교",
            "admission_date": date(2020, 3, 1),
            "graduation_date": date(2024, 2, 29),
            "is_transfer": False,
            "major_name": "컴퓨터공학",
            "gpa": 4.1,
            "max_gpa": 4.5,
        },
    )

    assert "기간: 2020-03-01 ~ 2024-02-29" in text
    assert "입학 구분: 일반 입학" in text
    assert "학점: 4.1 / 4.5" in text


@pytest.mark.parametrize(
    ("source_type", "row", "expected"),
    [
        (ResumeSourceType.ACTIVITY, {"activity_type": "동아리"}, "활동 유형: 동아리"),
        (ResumeSourceType.TRAINING, {"name": "AI 교육"}, "교육명: AI 교육"),
        (ResumeSourceType.CERTIFICATION, {"name": "정보처리기사"}, "자격증: 정보처리기사"),
        (ResumeSourceType.AWARD, {"name": "최우수상"}, "수상명: 최우수상"),
        (
            ResumeSourceType.OVERSEAS_EXPERIENCE,
            {"country_name": "미국"},
            "국가: 미국",
        ),
        (
            ResumeSourceType.LANGUAGE,
            {"language_name": "영어", "proficiency_level": "BUSINESS"},
            "언어: 영어",
        ),
    ],
)
def test_each_source_type_has_a_deterministic_template(source_type, row, expected):
    assert expected in build_resume_text(source_type, row)


def test_build_resume_chunks_sorts_rows_and_hashes_content():
    rows = [
        {"id": 2, "name": "SQLD"},
        {"id": 1, "name": "정보처리기사"},
    ]
    first = build_resume_chunks(7, ResumeSourceType.CERTIFICATION, rows)
    second = build_resume_chunks(7, ResumeSourceType.CERTIFICATION, rows)

    assert [chunk.source_id for chunk in first] == [1, 2]
    assert first == second
    assert all(len(chunk.content_hash) == 64 for chunk in first)

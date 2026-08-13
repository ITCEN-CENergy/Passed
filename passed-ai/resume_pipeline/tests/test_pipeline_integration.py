from __future__ import annotations

import logging
import os
from uuid import uuid4

import pytest

from resume_pipeline.pipeline import run_chunking_for_user


@pytest.fixture
def test_db_connection():
    """명시적인 테스트 DB에서만 실행하고, 테스트 데이터는 항상 롤백한다."""
    database_url = os.getenv("TEST_DATABASE_URL")
    if not database_url:
        pytest.skip("TEST_DATABASE_URL이 없어 PostgreSQL 통합 테스트를 건너뜁니다.")

    psycopg = pytest.importorskip("psycopg")
    dict_row = pytest.importorskip("psycopg.rows").dict_row
    conn = psycopg.connect(database_url, row_factory=dict_row)
    try:
        yield conn
    finally:
        conn.rollback()
        conn.close()


def _insert_test_documents(conn) -> tuple[int, int]:
    unique = uuid4().hex
    long_answer = (
        "저는 팀 프로젝트에서 Spring Boot 기반 채용 서비스의 API를 개발했습니다. "
        "요구사항을 작은 작업으로 나누고 팀원과 API 계약을 먼저 합의했습니다. "
        "PostgreSQL 실행 계획을 확인해 인덱스를 보완했고 반복 조회 쿼리도 줄였습니다. "
        "그 결과 주요 조회 응답 시간을 이전보다 안정적으로 낮출 수 있었습니다.\n\n"
        "배포 직전에는 예상하지 못한 동시성 오류가 발생했습니다. 로그와 재현 테스트를 "
        "바탕으로 트랜잭션 범위와 행 잠금 순서를 점검했고, 실패 상황을 먼저 재현하는 "
        "통합 테스트를 추가했습니다. 이후 같은 문제가 다시 발생하지 않도록 원인과 해결 "
        "과정을 문서화해 팀에 공유했습니다. 이 경험을 통해 구현 속도뿐 아니라 관찰 가능성과 "
        "재실행 가능한 검증 절차가 서비스 안정성에 중요하다는 점을 배웠습니다.\n\n"
        "입사 후에도 Java와 Python 서비스를 함께 다루며 데이터 파이프라인의 신뢰성을 "
        "높이겠습니다. 사용자의 이력서와 자기소개서에서 근거를 정확히 추적하고, 결과가 "
        "달라졌을 때 어떤 원본이 영향을 주었는지 설명 가능한 시스템을 만들겠습니다."
    )

    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO users (name, email, password) VALUES (%s, %s, %s) RETURNING id",
            ("청킹 통합 테스트", f"resume-pipeline-{unique}@example.com", "test-only"),
        )
        user_id = int(cur.fetchone()["id"])

        cur.execute("INSERT INTO resumes (user_id) VALUES (%s) RETURNING id", (user_id,))
        resume_id = int(cur.fetchone()["id"])
        cur.execute(
            "INSERT INTO educations "
            "(resume_id, school_name, admission_date, graduation_date, major_name, gpa, max_gpa) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s)",
            (resume_id, "테스트대학교", "2020-03-01", "2024-02-29", "컴퓨터공학", 4.1, 4.5),
        )
        cur.execute(
            "INSERT INTO experiences "
            "(resume_id, company_name, start_date, is_working, position, responsibilities, career_desc) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s)",
            (
                resume_id,
                "테스트테크",
                "2024-01-02",
                True,
                "백엔드 개발자",
                "Spring Boot API와 PostgreSQL 쿼리 개발",
                "응답 시간 개선과 통합 테스트 구축",
            ),
        )

        # 질문 마스터는 서비스 공용 데이터입니다. 기존 행이 있으면 그대로 사용하고,
        # 비어 있는 테스트 DB에서만 테스트 트랜잭션 안에 질문을 만듭니다.
        cur.execute(
            "SELECT id FROM cover_letter_questions WHERE question_type = 'EXPERIENCE'"
        )
        question = cur.fetchone()
        if question is None:
            cur.execute(
                "INSERT INTO cover_letter_questions (question_type, question_text, is_active) "
                "VALUES ('EXPERIENCE', %s, TRUE) RETURNING id",
                ("문제를 해결한 경험을 설명해 주세요.",),
            )
            question_id = int(cur.fetchone()["id"])
        else:
            question_id = int(question["id"])

        cur.execute("INSERT INTO cover_letters (user_id) VALUES (%s) RETURNING id", (user_id,))
        cover_letter_id = int(cur.fetchone()["id"])
        cur.execute(
            "INSERT INTO cover_letter_items (cover_letter_id, question_id, answer) "
            "VALUES (%s, %s, %s) RETURNING id",
            (cover_letter_id, question_id, long_answer),
        )
        item_id = int(cur.fetchone()["id"])

    return user_id, item_id


@pytest.mark.integration
def test_real_postgresql_chunks_resume_and_long_cover_letter(test_db_connection, caplog):
    user_id, item_id = _insert_test_documents(test_db_connection)

    with caplog.at_level(logging.INFO, logger="resume_pipeline.pipeline"):
        first = run_chunking_for_user(test_db_connection, user_id)

    assert first.resume_chunks.inserted == 2
    assert first.cover_letter_chunks.inserted >= 2
    assert "table=educations rows=1 chunks=1" in caplog.text
    assert "table=experiences rows=1 chunks=1" in caplog.text

    with test_db_connection.cursor() as cur:
        cur.execute(
            "SELECT chunk_index, chunk_content FROM cover_letter_chunks "
            "WHERE cover_letter_item_id = %s ORDER BY chunk_index",
            (item_id,),
        )
        stored_cover_chunks = cur.fetchall()
    assert len(stored_cover_chunks) == first.cover_letter_chunks.inserted
    assert all(row["chunk_content"].strip() for row in stored_cover_chunks)

    # content_hash가 같은 두 번째 실행은 INSERT/UPDATE 없이 전부 unchanged여야 합니다.
    second = run_chunking_for_user(test_db_connection, user_id)
    assert second.resume_chunks.unchanged == 2
    assert second.cover_letter_chunks.unchanged == len(stored_cover_chunks)

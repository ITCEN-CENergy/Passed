"""오프라인 단위 테스트(계획서 15절).

DB/LLM 없이 실행 가능한 결정적 로직만 검증한다.
"""

from __future__ import annotations

from types import SimpleNamespace

import pytest

from job_posting_pipeline.chunker import (
    EMPTY_CONTENT_HASH,
    build_chunks,
    content_hash,
    split_list_items,
    split_narrative,
    token_count,
)
from job_posting_pipeline.company_assignment import assign_company_id
from job_posting_pipeline.csv_loader import (
    CSVRowError,
    read_csv_rows,
    restore_missing_job_posting_ids,
)
from job_posting_pipeline.models import ExtractedItem, SourceType, use_for_matching
from job_posting_pipeline.normalize import (
    normalize_edu_level,
    normalize_hire_type,
    normalize_region,
    normalize_text,
)


# 테스트는 오프라인에서 실행되므로 tiktoken 인코딩 다운로드를 피하기 위해
# 결정적 가짜 토크나이저(문자 단위)를 주입한다. 운영에서는 tiktoken cl100k_base 사용.
class _FakeEnc:
    def encode(self, text: str) -> list[str]:
        return list(text)

    def decode(self, tokens: list[str]) -> str:
        return "".join(tokens)


import job_posting_pipeline.chunker as _chunker_mod  # noqa: E402
import job_posting_pipeline.chunk_sync as _chunk_sync_mod  # noqa: E402
import job_posting_pipeline.csv_loader as _csv_loader_mod  # noqa: E402
import job_posting_pipeline.embedding_worker as _embedding_worker_mod  # noqa: E402
import job_posting_pipeline.queries as _queries_mod  # noqa: E402

_chunker_mod._enc = _FakeEnc()  # type: ignore[attr-defined]


# --- 임베딩 API/작업자 ---

def test_create_embeddings_restores_response_index_order(monkeypatch):
    settings = SimpleNamespace(
        embedding_model="openai/text-embedding-3-small",
        embedding_dimension=3,
        embedding_max_retries=2,
    )
    monkeypatch.setattr(_embedding_worker_mod, "get_settings", lambda: settings)

    response = SimpleNamespace(
        # API 응답 순서가 입력 순서와 다르더라도 index로 복원해야 한다.
        data=[
            SimpleNamespace(index=1, embedding=[0.4, 0.5, 0.6]),
            SimpleNamespace(index=0, embedding=[0.1, 0.2, 0.3]),
        ],
        usage=SimpleNamespace(prompt_tokens=17),
    )
    client = SimpleNamespace(
        embeddings=SimpleNamespace(create=lambda **kwargs: response)
    )

    result = _embedding_worker_mod._create_embeddings(
        ["첫 번째", "두 번째"],
        client=client,
    )

    assert result.vectors == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    assert result.prompt_tokens == 17


def test_create_embeddings_rejects_wrong_dimension(monkeypatch):
    settings = SimpleNamespace(
        embedding_model="openai/text-embedding-3-small",
        embedding_dimension=3,
        embedding_max_retries=1,
    )
    monkeypatch.setattr(_embedding_worker_mod, "get_settings", lambda: settings)
    response = SimpleNamespace(
        data=[SimpleNamespace(index=0, embedding=[0.1, 0.2])],
        usage=SimpleNamespace(prompt_tokens=3),
    )
    client = SimpleNamespace(
        embeddings=SimpleNamespace(create=lambda **kwargs: response)
    )

    with pytest.raises(
        _embedding_worker_mod.InvalidEmbeddingResponseError,
        match="차원 불일치",
    ):
        _embedding_worker_mod._create_embeddings(["문장"], client=client)


def test_process_batch_failure_returns_false_without_db_update(monkeypatch):
    stats = _embedding_worker_mod.EmbeddingRunStats()

    monkeypatch.setattr(
        _embedding_worker_mod,
        "_create_embeddings",
        lambda texts, client: (_ for _ in ()).throw(RuntimeError("API 실패")),
    )

    succeeded = _embedding_worker_mod._process_batch(
        conn=object(),
        rows=[(1, "청크 내용", "hash")],
        stats=stats,
        client=object(),
    )

    assert succeeded is False
    assert stats.failed == 1


# --- CSV 인코딩 ---

def test_read_csv_rows_utf8(tmp_path):
    path = tmp_path / "utf8.csv"
    path.write_text("job_posting_id,title\n1,백엔드 개발자\n", encoding="utf-8-sig")

    rows, encoding = read_csv_rows(path)

    assert encoding == "utf-8-sig"
    assert rows[0]["job_posting_id"] == "1"
    assert rows[0]["title"] == "백엔드 개발자"


def test_read_csv_rows_cp949_fallback(tmp_path):
    path = tmp_path / "cp949.csv"
    path.write_text("job_posting_id,title\n1,백엔드 개발자\n", encoding="cp949")

    rows, encoding = read_csv_rows(path)

    assert encoding == "cp949"
    assert rows[0]["job_posting_id"] == "1"
    assert rows[0]["title"] == "백엔드 개발자"


def test_restore_missing_job_posting_ids():
    rows = [
        {"job_role_id": str(role_id), "title": str(index)}
        for role_id in (1, 2)
        for index in range(10)
    ]
    restored = restore_missing_job_posting_ids(rows)
    assert restored[0]["job_posting_id"] == "1"
    assert restored[9]["job_posting_id"] == "10"
    assert restored[10]["job_posting_id"] == "11"
    assert restored[-1]["job_posting_id"] == "20"


def test_restore_missing_job_posting_ids_rejects_unknown_shape():
    with pytest.raises(CSVRowError, match="직무별 10건 fixture"):
        restore_missing_job_posting_ids([{"job_role_id": "1"}])


def test_load_csv_stops_before_insert_when_job_roles_are_missing(
    monkeypatch, tmp_path
):
    record = {"id": 1841, "company_id": 0, "job_role_id": 185}
    insert_called = False

    monkeypatch.setattr(
        _csv_loader_mod,
        "read_csv_rows",
        lambda path: ([{"row": "1", "job_posting_id": "1841"}], "utf-8-sig"),
    )
    monkeypatch.setattr(_csv_loader_mod, "parse_row", lambda row: record)
    monkeypatch.setattr(_csv_loader_mod, "check_company_ids", lambda conn, ids: [])
    monkeypatch.setattr(
        _csv_loader_mod, "_check_job_roles_exist", lambda conn, ids: [185]
    )

    def unexpected_insert(conn, parsed):
        nonlocal insert_called
        insert_called = True

    monkeypatch.setattr(_csv_loader_mod, "_upsert_posting", unexpected_insert)

    with pytest.raises(_csv_loader_mod.CSVRowError, match="job_roles.id 누락"):
        _csv_loader_mod.load_csv(object(), tmp_path / "input.csv")

    assert insert_called is False


def test_load_csv_savepoint_keeps_processing_after_one_row_failure(
    monkeypatch, tmp_path
):
    records = [
        {"id": 1, "company_id": 0, "job_role_id": 185},
        {"id": 2, "company_id": 0, "job_role_id": 185},
    ]

    class FakeTransaction:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, traceback):
            return False

    class FakeConnection:
        transaction_count = 0

        def transaction(self):
            self.transaction_count += 1
            return FakeTransaction()

    conn = FakeConnection()
    attempts: list[int] = []

    monkeypatch.setattr(
        _csv_loader_mod,
        "read_csv_rows",
        lambda path: (
            [
                {"id": "1", "job_posting_id": "1"},
                {"id": "2", "job_posting_id": "2"},
            ],
            "utf-8-sig",
        ),
    )
    monkeypatch.setattr(
        _csv_loader_mod, "parse_row", lambda row: records[int(row["id"]) - 1]
    )
    monkeypatch.setattr(_csv_loader_mod, "check_company_ids", lambda conn, ids: [])
    monkeypatch.setattr(
        _csv_loader_mod, "_check_job_roles_exist", lambda conn, ids: []
    )
    monkeypatch.setattr(_csv_loader_mod, "_fix_sequence", lambda conn: None)

    def upsert(conn, record):
        attempts.append(record["id"])
        if record["id"] == 1:
            raise RuntimeError("첫 행 실패")

    monkeypatch.setattr(_csv_loader_mod, "_upsert_posting", upsert)

    result = _csv_loader_mod.load_csv(conn, tmp_path / "input.csv")

    assert attempts == [1, 2]
    assert conn.transaction_count == 2
    assert result.loaded == 1
    assert result.failed == 1


# --- 표기 정규화 ---

def test_region_alias_to_standard():
    assert normalize_region("서울") == "서울특별시"
    assert normalize_region("경기") == "경기도"
    assert normalize_region("제주") == "제주특별자치도"


def test_hire_and_edu_normalization():
    assert normalize_hire_type("정규직 ") == "정규직"
    assert normalize_edu_level("학사 이상") == "학사 이상"


# --- 공통 정규화 ---

def test_normalize_text_strips_bullets_and_collapses_blanks():
    raw = "• 항목1\n• 항목2\n\n\n\n• 항목3"
    out = normalize_text(raw)
    # 계획서: 연속된 빈 줄은 하나로 줄인다(불릿은 제거, 항목 내용은 보존)
    assert out == "항목1\n항목2\n\n항목3"


def test_normalize_text_handles_crlf():
    assert normalize_text("a\r\nb\r\n") == "a\nb"


# --- company_id 배정 ---

def test_company_id_deterministic_and_in_range():
    a = assign_company_id(1)
    b = assign_company_id(1)
    assert a == b
    assert 0 <= a <= 159
    c = assign_company_id(2)
    assert 0 <= c <= 159


# --- 목록 분리 ---

def test_split_list_items_per_line():
    assert split_list_items("A\nB\nC") == ["A", "B", "C"]
    assert split_list_items("  \n\n") == []


def test_bullet_list_chunked_per_item():
    posting = {
        "id": 1,
        "title": "t",
        "position_detail": None,
        "main_duty": "• Kotlin 활용\n• 대규모 트래픽 처리",
        "qualification": None,
        "preference": None,
        "disqualify_reason": None,
        "process": None,
    }
    chunks = build_chunks(posting, [], [], max_tokens=400, overlap=50)
    main = [c for c in chunks if c.source_type == SourceType.MAIN_TASK]
    assert len(main) == 2
    assert main[0].chunk_index == 0
    assert main[1].chunk_index == 1
    assert main[0].chunk_content == "Kotlin 활용"


# --- 토큰 분할/오버랩 ---

def test_narrative_overlap_split():
    text = "문단1 " * 600
    pieces = split_narrative(text, max_tokens=50, overlap=10)
    assert len(pieces) >= 2
    assert all(token_count(p) <= 50 for p in pieces)


def test_build_chunks_splits_non_empty_position_detail():
    posting = {
        "id": 1,
        "title": "백엔드 개발자",
        "position_detail": "백엔드 API를 개발하고 운영합니다. " * 30,
        "main_duty": None,
        "qualification": None,
        "preference": None,
        "disqualify_reason": None,
        "process": None,
    }

    chunks = build_chunks(posting, [], [], max_tokens=50, overlap=10)
    position_chunks = [
        chunk
        for chunk in chunks
        if chunk.source_type == SourceType.POSITION_DETAIL
    ]

    assert len(position_chunks) >= 2
    assert all(chunk.chunk_content for chunk in position_chunks)
    assert all(token_count(chunk.chunk_content) <= 50 for chunk in position_chunks)


# --- content_hash/빈 청크 ---

def test_same_normalized_text_same_hash():
    assert content_hash("hello") == content_hash("hello")
    assert content_hash("a") != content_hash("b")


def test_empty_element_produces_one_empty_chunk():
    posting = {
        "id": 1, "title": "t", "position_detail": None, "main_duty": None,
        "qualification": None, "preference": None, "disqualify_reason": None,
        "process": None,
    }
    chunks = build_chunks(posting, [], [], max_tokens=400, overlap=50)
    assert any(
        c.source_type == SourceType.MAIN_TASK and c.chunk_content == ""
        and c.content_hash == EMPTY_CONTENT_HASH for c in chunks
    )


def test_empty_chunks_are_blank():
    chunks = build_chunks(
        {"id": 1, "title": "t"}, [], [], 400, 50
    )
    assert any(c.chunk_content == "" for c in chunks)


# --- use_for_matching ---

def test_use_for_matching_rule():
    assert use_for_matching(SourceType.POSITION_DETAIL) is True
    assert use_for_matching(SourceType.TECH_STACK) is True
    assert use_for_matching(SourceType.PROCESS) is False
    assert use_for_matching(SourceType.DISQUALIFICATION) is False
    assert use_for_matching(SourceType.BENEFIT) is False


# --- 기술 스택 정규화/중복 ---

def test_tech_stack_alias_and_dedup():
    posting = {"id": 1, "title": "t", "position_detail": None,
               "main_duty": None, "qualification": None, "preference": None,
               "disqualify_reason": None, "process": None}
    tech = [ExtractedItem("kotlin", "Kotlin 활용"), ExtractedItem("Kotlin", "Kotlin 활용")]
    chunks = build_chunks(posting, tech, [], 400, 50)
    tech_chunks = [c for c in chunks if c.source_type == SourceType.TECH_STACK]
    assert len(tech_chunks) == 1
    assert tech_chunks[0].chunk_content == "Kotlin"
    assert tech_chunks[0].use_for_matching_flag is True


def test_benefit_use_for_matching_false():
    posting = {"id": 1, "title": "t", "position_detail": None,
               "main_duty": None, "qualification": None, "preference": None,
               "disqualify_reason": None, "process": None}
    benefits = [ExtractedItem("자율복장", "자율복장")]
    chunks = build_chunks(posting, [], benefits, 400, 50)
    ben = [c for c in chunks if c.source_type == SourceType.BENEFIT]
    assert len(ben) == 1
    assert ben[0].use_for_matching_flag is False


def test_process_kept_as_single_chunk():
    posting = {"id": 1, "title": "t", "position_detail": None, "main_duty": None,
               "qualification": None, "preference": None, "disqualify_reason": None,
               "process": "서류전형 > 직무과제 > 실무면접 > 최종합격"}
    chunks = build_chunks(posting, [], [], 400, 50)
    proc = [c for c in chunks if c.source_type == SourceType.PROCESS]
    assert len(proc) == 1
    assert ">" in proc[0].chunk_content
    assert proc[0].use_for_matching_flag is False


def test_current_db_sql_does_not_reference_removed_matching_column():
    sql = " ".join((
        _chunk_sync_mod._INSERT_SQL,
        _chunk_sync_mod._UPDATE_SQL,
        _embedding_worker_mod._PENDING_SQL,
        _embedding_worker_mod._UPDATE_EMBEDDING_SQL,
        _queries_mod.MATCHING_CHUNK_WHERE,
    ))
    assert "use_for_matching" not in sql
    assert "embedding_model" in sql
    assert "embedding_status" in sql


def test_flyway_v3_source_types_are_exact():
    assert _chunk_sync_mod.DB_SOURCE_TYPES == {
        "POSITION_DETAIL", "MAIN_TASK", "REQUIREMENT", "PREFERENCE",
        "BENEFIT", "PROCESS", "DISQUALIFICATION",
    }
    assert "TECH_STACK" not in _chunk_sync_mod.DB_SOURCE_TYPES


def test_job_posting_upsert_supports_generated_always_identity():
    assert "OVERRIDING SYSTEM VALUE" in _csv_loader_mod._UPSERT_SQL

from __future__ import annotations

from contextlib import contextmanager
from types import SimpleNamespace

import pytest
from tenacity import wait_none

import resume_pipeline.embedding_worker as worker
import resume_pipeline.run_embedding as run_embedding


class FakeCursor:
    """embedding_worker가 사용하는 SQL 계약만 흉내 내는 메모리 cursor."""

    def __init__(self, conn):
        self.conn = conn
        self.result = []
        self.rowcount = 0

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql, params=()):
        compact = " ".join(str(sql).split())
        self.conn.executed.append((compact, params))
        self.rowcount = 0

        if compact.startswith("SELECT COUNT(*)"):
            pending = [row for row in self.conn.rows if row["status"] == "PENDING"]
            if "BTRIM(chunk_content) = ''" in compact:
                count = sum(not row["content"].strip() for row in pending)
            else:
                count = sum(bool(row["content"].strip()) for row in pending)
            self.result = [(count,)]
            return

        if compact.startswith("SELECT id, chunk_content, content_hash"):
            limit = int(params[-1])
            selected = [
                row
                for row in self.conn.rows
                if row["status"] == "PENDING" and row["content"].strip()
            ][:limit]
            self.result = [
                {
                    "id": row["id"],
                    "chunk_content": row["content"],
                    "content_hash": row["hash"],
                }
                for row in selected
            ]
            return

        if "embedding_status = 'COMPLETED'" in compact:
            vector, model, row_id, expected_hash = params
            row = self.conn.find(row_id)
            if row and row["status"] == "PENDING" and row["hash"] == expected_hash:
                row.update(status="COMPLETED", vector=vector, model=model)
                self.rowcount = 1
            return

        if "embedding_status = 'FAILED'" in compact:
            row_id, expected_hash = params
            row = self.conn.find(row_id)
            if row and row["status"] == "PENDING" and row["hash"] == expected_hash:
                row["status"] = "FAILED"
                self.rowcount = 1
            return

        raise AssertionError(f"예상하지 못한 SQL: {compact}")

    def fetchone(self):
        return self.result[0]

    def fetchall(self):
        return list(self.result)


class FakeConnection:
    def __init__(self, rows):
        self.rows = rows
        self.executed = []
        self.commits = 0

    def cursor(self):
        return FakeCursor(self)

    def commit(self):
        self.commits += 1

    def find(self, row_id):
        return next((row for row in self.rows if row["id"] == row_id), None)


class FakeEmbeddingsAPI:
    def __init__(self, *, fail_calls=(), before_response=None):
        self.calls = []
        self.fail_calls = set(fail_calls)
        self.before_response = before_response

    def create(self, **kwargs):
        call_number = len(self.calls) + 1
        texts = list(kwargs["input"])
        self.calls.append(texts)
        if call_number in self.fail_calls:
            raise RuntimeError(f"fake failure call={call_number}")
        if self.before_response:
            self.before_response(call_number)

        # 일부러 역순으로 반환해 worker가 index 기준으로 복구하는지 검증한다.
        data = [
            SimpleNamespace(index=index, embedding=[float(index), 0.2, 0.3])
            for index in reversed(range(len(texts)))
        ]
        return SimpleNamespace(data=data)


def _rows(count, *, status="PENDING"):
    return [
        {
            "id": index + 1,
            "content": f"청크 {index + 1}",
            "hash": f"hash-{index + 1}",
            "status": status,
            "vector": None,
            "model": None,
        }
        for index in range(count)
    ]


@pytest.fixture(autouse=True)
def small_embedding_dimension(monkeypatch):
    # 실제 1536개 float를 매 테스트마다 만들 필요는 없으므로 계약 검사만 3차원으로 축소한다.
    monkeypatch.setattr(worker, "EMBEDDING_DIM", 3)


def test_only_pending_rows_are_embedded_and_completed(monkeypatch):
    rows = _rows(2)
    rows.append({**_rows(1, status="COMPLETED")[0], "id": 3})
    conn = FakeConnection(rows)
    api = FakeEmbeddingsAPI()
    monkeypatch.setattr(worker, "_create_openai_client", lambda: SimpleNamespace(embeddings=api))

    stats = worker.embed_pending_chunks(conn, "resume_chunks")

    assert stats.embedded == 2
    assert [row["status"] for row in rows] == ["COMPLETED", "COMPLETED", "COMPLETED"]
    assert len(api.calls) == 1


def test_250_rows_are_sent_as_100_100_50_batches(monkeypatch):
    conn = FakeConnection(_rows(250))
    api = FakeEmbeddingsAPI()
    monkeypatch.setattr(worker, "_create_openai_client", lambda: SimpleNamespace(embeddings=api))

    stats = worker.embed_pending_chunks(conn, "resume_chunks", batch_size=100)

    assert [len(call) for call in api.calls] == [100, 100, 50]
    assert stats.embedded == 250
    assert stats.failed == 0
    assert conn.commits == 3
    assert all(row["status"] == "COMPLETED" for row in conn.rows)


def test_response_is_restored_by_index_before_saving(monkeypatch):
    conn = FakeConnection(_rows(2))
    api = FakeEmbeddingsAPI()
    monkeypatch.setattr(worker, "_create_openai_client", lambda: SimpleNamespace(embeddings=api))

    worker.embed_pending_chunks(conn, "cover_letter_chunks")

    assert conn.rows[0]["vector"][0] == 0.0
    assert conn.rows[1]["vector"][0] == 1.0


def test_failed_batch_is_marked_failed_and_next_batch_continues(monkeypatch):
    conn = FakeConnection(_rows(3))
    api = FakeEmbeddingsAPI(fail_calls={1})
    monkeypatch.setattr(worker, "_create_openai_client", lambda: SimpleNamespace(embeddings=api))

    stats = worker.embed_pending_chunks(conn, "resume_chunks", batch_size=2)

    assert stats.failed == 2
    assert stats.embedded == 1
    assert [row["status"] for row in conn.rows] == ["FAILED", "FAILED", "COMPLETED"]
    assert len(api.calls) == 2
    assert conn.commits == 2


def test_blank_chunk_is_logged_and_left_pending_without_api_call(monkeypatch, caplog):
    conn = FakeConnection(
        [{"id": 1, "content": "   ", "hash": "h", "status": "PENDING", "vector": None}]
    )
    monkeypatch.setattr(
        worker,
        "_create_openai_client",
        lambda: pytest.fail("빈 청크는 API client도 만들면 안 됩니다."),
    )

    with caplog.at_level("WARNING"):
        stats = worker.embed_pending_chunks(conn, "resume_chunks")

    assert stats.skipped == 1
    assert conn.rows[0]["status"] == "PENDING"
    assert "상태는 변경하지 않습니다" in caplog.text


def test_content_hash_change_rejects_stale_vector_and_retries_new_text(monkeypatch):
    conn = FakeConnection(_rows(1))

    def change_source_after_first_request(call_number):
        if call_number == 1:
            conn.rows[0]["hash"] = "new-hash"
            conn.rows[0]["content"] = "수정된 청크"

    api = FakeEmbeddingsAPI(before_response=change_source_after_first_request)
    monkeypatch.setattr(worker, "_create_openai_client", lambda: SimpleNamespace(embeddings=api))

    stats = worker.embed_pending_chunks(conn, "resume_chunks")

    assert stats.skipped == 1
    assert stats.embedded == 1
    assert len(api.calls) == 2
    assert api.calls[1] == ["수정된 청크"]
    assert conn.rows[0]["status"] == "COMPLETED"


def test_table_and_filter_are_whitelisted():
    conn = FakeConnection([])
    with pytest.raises(ValueError, match="지원하지 않는"):
        worker.embed_pending_chunks(conn, "users")
    with pytest.raises(ValueError, match="허용되지 않은 필터"):
        worker.embed_pending_chunks(
            conn,
            "resume_chunks",
            filter_sql="AND 1 = 1; DROP TABLE users",
        )


def test_transient_error_is_retried_up_to_success(monkeypatch):
    attempts = 0

    def flaky_request(client, texts):
        nonlocal attempts
        attempts += 1
        if attempts < 3:
            raise worker.TransientEmbeddingError("temporary")
        return SimpleNamespace(
            data=[SimpleNamespace(index=0, embedding=[0.1, 0.2, 0.3])]
        )

    monkeypatch.setattr(worker, "_request_embeddings", flaky_request)
    monkeypatch.setattr(worker, "wait_exponential", lambda **kwargs: wait_none())

    vectors = worker._create_embeddings(object(), ["재시도할 문장"])

    assert attempts == 3
    assert vectors == [[0.1, 0.2, 0.3]]


def test_wrong_embedding_dimension_is_rejected(monkeypatch):
    monkeypatch.setattr(
        worker,
        "_request_embeddings",
        lambda client, texts: SimpleNamespace(
            data=[SimpleNamespace(index=0, embedding=[0.1, 0.2])]
        ),
    )

    with pytest.raises(worker.InvalidEmbeddingResponseError, match="차원 불일치"):
        worker._create_embeddings(object(), ["차원이 잘못된 문장"])


def test_dry_run_does_not_require_api_key_or_call_worker(monkeypatch):
    fake_conn = object()

    @contextmanager
    def fake_connection():
        yield fake_conn

    monkeypatch.setattr(run_embedding, "connection", fake_connection)
    monkeypatch.setattr(run_embedding, "validate_embedding_schema", lambda conn: None)
    monkeypatch.setattr(
        run_embedding,
        "count_pending_chunks",
        lambda conn, table, **kwargs: 3 if table == "resume_chunks" else 2,
    )
    monkeypatch.setattr(
        run_embedding,
        "embed_pending_chunks",
        lambda *args, **kwargs: pytest.fail("dry-run은 worker를 호출하면 안 됩니다."),
    )
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    assert run_embedding.main(["--user-id", "19", "--dry-run"]) == 0

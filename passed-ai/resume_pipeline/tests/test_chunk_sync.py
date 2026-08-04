from __future__ import annotations

from resume_pipeline.chunk_sync import sync_cover_letter_chunks, sync_resume_chunks
from resume_pipeline.models import (
    CoverLetterChunk,
    ResumeChunk,
    ResumeSourceType,
    SyncStats,
)


class FakeCursor:
    def __init__(self, *, resume_rows=None, cover_rows=None):
        self.resume_rows = resume_rows or []
        self.cover_rows = cover_rows or []
        self.executed: list[tuple[str, tuple | None]] = []
        self._result = []

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def execute(self, sql, params=None):
        compact = " ".join(sql.split())
        self.executed.append((compact, params))
        if compact.startswith("SELECT id FROM resumes"):
            self._result = [(params[0],)]
        elif compact.startswith("SELECT id FROM cover_letter_items"):
            self._result = [(params[0],)]
        elif compact.startswith("SELECT source_type"):
            self._result = list(self.resume_rows)
        elif compact.startswith("SELECT chunk_index"):
            self._result = list(self.cover_rows)
        else:
            self._result = []

    def fetchone(self):
        return self._result[0] if self._result else None

    def fetchall(self):
        return list(self._result)


class FakeConnection:
    def __init__(self, cursor):
        self.fake_cursor = cursor

    def cursor(self):
        return self.fake_cursor


def test_resume_sync_inserts_updates_deletes_and_skips_unchanged():
    cursor = FakeCursor(
        resume_rows=[
            ("EXPERIENCE", 1, 0, "same"),
            ("EDUCATION", 2, 0, "old"),
            ("AWARD", 3, 0, "deleted"),
        ]
    )
    chunks = [
        ResumeChunk(7, ResumeSourceType.EXPERIENCE, 1, 0, "same text", "same"),
        ResumeChunk(7, ResumeSourceType.EDUCATION, 2, 0, "new text", "new"),
        ResumeChunk(7, ResumeSourceType.CERTIFICATION, 4, 0, "certificate", "cert"),
    ]

    stats = sync_resume_chunks(FakeConnection(cursor), 7, chunks)

    assert stats == SyncStats(inserted=1, updated=1, deleted=1, unchanged=1)
    statements = "\n".join(sql for sql, _ in cursor.executed)
    assert "embedding_status = 'PENDING'" in statements
    assert "INSERT INTO resume_chunks" in statements
    assert "DELETE FROM resume_chunks" in statements


def test_cover_letter_sync_uses_item_scope_and_resets_embedding():
    cursor = FakeCursor(cover_rows=[(0, "old"), (1, "remove")])
    chunks = [CoverLetterChunk(9, 0, "changed", "new")]

    stats = sync_cover_letter_chunks(FakeConnection(cursor), 9, chunks)

    assert stats == SyncStats(inserted=0, updated=1, deleted=1, unchanged=0)
    statements = "\n".join(sql for sql, _ in cursor.executed)
    assert "UPDATE cover_letter_chunks" in statements
    assert "embedding_updated_at = NULL" in statements
    assert "DELETE FROM cover_letter_chunks" in statements

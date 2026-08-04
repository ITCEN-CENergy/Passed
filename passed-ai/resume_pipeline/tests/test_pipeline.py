from __future__ import annotations

import pytest

from resume_pipeline.pipeline import MissingResumeError, run_chunking_for_user


class _MissingResumeCursor:
    """필수 이력서 누락 경로만 검증하는 최소 단위 테스트용 cursor."""

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql, params=None):
        self.sql = sql
        self.params = params

    def fetchone(self):
        return None


class _MissingResumeConnection:
    def cursor(self):
        return _MissingResumeCursor()


def test_missing_required_resume_raises_clear_error():
    with pytest.raises(MissingResumeError, match=r"user_id=123"):
        run_chunking_for_user(_MissingResumeConnection(), user_id=123)

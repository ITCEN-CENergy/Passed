from contextlib import contextmanager
from types import SimpleNamespace

from fastapi.testclient import TestClient
import pytest

from api.features.user_skill.schema import UserSkillExtractionResponse
from api.features.user_skill import router as user_skill_router
from api.features.user_skill import service
from app.main import app


client = TestClient(app)


def test_user_skill_extraction_endpoint_returns_camel_case_response(monkeypatch):
    monkeypatch.setattr(
        user_skill_router,
        "run_user_skill_analysis",
        lambda user_id: UserSkillExtractionResponse(
            user_id=user_id,
            processed_chunk_count=7,
            skill_count=5,
            unmapped_count=1,
            persisted=True,
            resume_chunks_embedded=3,
            cover_letter_chunks_embedded=2,
        ),
    )

    response = client.post(
        "/api/v1/user-skills/extractions",
        json={"userId": 257},
    )

    assert response.status_code == 200
    assert response.json() == {
        "userId": 257,
        "processedChunkCount": 7,
        "skillCount": 5,
        "unmappedCount": 1,
        "persisted": True,
        "resumeChunksEmbedded": 3,
        "coverLetterChunksEmbedded": 2,
    }


def test_user_skill_extraction_endpoint_rejects_invalid_user_id():
    response = client.post(
        "/api/v1/user-skills/extractions",
        json={"userId": 0},
    )

    assert response.status_code == 422


def test_pipeline_runs_chunking_embedding_mapping_and_persistence(monkeypatch):
    calls: list[str] = []
    fake_connection = object()

    @contextmanager
    def connection():
        yield fake_connection

    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setattr(service, "connection", connection)
    monkeypatch.setattr(
        service,
        "validate_embedding_schema",
        lambda conn: calls.append("validate-schema"),
    )
    monkeypatch.setattr(
        service,
        "run_chunking_for_user",
        lambda conn, user_id: calls.append("chunk"),
    )
    monkeypatch.setattr(
        service,
        "_retry_failed_embeddings",
        lambda conn, user_id: calls.append("retry-failed"),
    )

    def embed(conn, table, **kwargs):
        calls.append(f"embed:{table}")
        return SimpleNamespace(embedded=2 if table == "resume_chunks" else 1, failed=0)

    extraction = SimpleNamespace(failures=[])
    report = SimpleNamespace(
        processed_chunk_count=6,
        skills=[object(), object(), object(), object()],
        unmapped=[object()],
    )
    monkeypatch.setattr(service, "embed_pending_chunks", embed)
    monkeypatch.setattr(
        service,
        "extract_user_skill_candidates",
        lambda conn, user_id: calls.append("extract") or extraction,
    )
    monkeypatch.setattr(
        service,
        "build_user_skill_mapping_report",
        lambda conn, value: calls.append("map") or report,
    )
    monkeypatch.setattr(
        service,
        "persist_user_skill_mapping",
        lambda conn, value: calls.append("persist"),
    )

    response = service.run_user_skill_analysis(257)

    assert calls == [
        "validate-schema",
        "chunk",
        "retry-failed",
        "embed:resume_chunks",
        "embed:cover_letter_chunks",
        "extract",
        "map",
        "persist",
    ]
    assert response.user_id == 257
    assert response.skill_count == 4
    assert response.persisted is True


def test_pipeline_does_not_start_without_openai_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    with pytest.raises(service.UserSkillPipelineConfigurationError):
        service.run_user_skill_analysis(257)
